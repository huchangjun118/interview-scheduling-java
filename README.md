# 招聘面试阶段流转与排期服务

基于 Spring Boot 的招聘面试排期服务：候选人按既定顺序推进招聘阶段，面试安排到
招聘官（面试官）与候选人都可行的时段。所有约束均由服务端保证，不接入外部日历
或邮件服务。

## 构建与测试

```bash
mvn compile   # 编译
mvn test      # 运行全部自动化测试（流转/冲突/并发/改期/越权/恢复）
mvn spring-boot:run  # 本地启动
```

## 领域状态语义

### 候选人阶段（Stage）

```
APPLIED -> SCREENING -> INTERVIEWING -> OFFER -> HIRED
```

- 只允许**单步向前**推进；回退、原地、越级、终态后再流转都会被拒绝。
- 拒绝时返回 `ILLEGAL_STAGE_TRANSITION`，消息中说明当前阶段、目标阶段以及
  唯一允许的下一阶段，并写入审计（`STAGE_ADVANCE_REJECTED`）。

### 面试状态（InterviewStatus）

| 状态 | 含义 | 是否占用时段 |
|---|---|---|
| `SCHEDULED` | 已安排 | 是 |
| `RESCHEDULED` | 已改期（时间以最新为准） | 是 |
| `CANCELLED` | 已取消，时段已释放 | 否 |
| `COMPLETED` | 已结束（保留语义，供扩展） | 否 |

- 只有 `SCHEDULED` / `RESCHEDULED` 参与冲突判定。
- 已开始（`now >= start`）或已结束的面试不得改期或取消
  （`INTERVIEW_ALREADY_STARTED`）；非活跃状态不得改期/取消
  （`INTERVIEW_NOT_ACTIVE`）。

### 时段语义

- 接口入参接受带时区偏移的 ISO-8601 时间（如 `2028-01-01T09:00:00+08:00`），
  服务端一律换算到 UTC 时间轴后再做判定与存储。
- 重叠判定采用**半开区间 `[start, end)`**：首尾相接（`end == 另一场.start`）
  不算冲突。
- 排期要求面试官与候选人事先维护的可用时段（`/availability/{ownerId}`）
  完整覆盖面试时段，否则按冲突处理。

## 并发、幂等与恢复

- **并发**：所有占用/释放时段的判定在同一把锁内完成。多个请求同时抢占同一
  时段时只有一个成功，其余收到 `409` 冲突结果，并附带最多 3 个替代时段
  （按 30 分钟步进向后搜索 7 天内双方均可行的时段）。
- **幂等**：排期请求必须携带 `requestId`。同一 `requestId` 重复提交返回首次
  的面试，不会产生重复记录；并发重复提交同样安全。
- **改期原子性**：改期在同一把锁内先校验新时段再一次性切换，失败时旧时段
  保持占用，不会出现"已释放却未占用"的空档。
- **恢复**：每次成功变更后将全量状态快照写入本地文件（临时文件 + 原子移动），
  重启时整体恢复。时段占用关系完全由面试集合推导，因此不会出现"时段被占用
  却查不到对应面试"的不一致。

## 角色权限

| 操作 | RECRUITER | CANDIDATE | ADMIN |
|---|---|---|---|
| 注册候选人/面试官、维护可用时段 | ✔ | ✔ | ✔ |
| 推进候选人阶段 | ✔ | ✘ | ✔ |
| 安排面试 | ✔ | ✘ | ✔ |
| 改期 | ✔ | ✘ | ✔ |
| 取消面试 | ✔ | 仅本人 | ✔ |
| 查询审计 | ✔ | ✔ | ✔ |

越权请求返回 `403`，并以 `PERMISSION_DENIED` 写入审计（含操作人与被拒原因）。

## 审计

任何一次安排、改期、取消、冲突与拒绝都会记录：动作类型、操作人、操作人角色、
目标对象、原因、发生时间。查询接口：

```
GET /audits                # 全部
GET /audits?targetId=xxx   # 按目标对象（候选人或面试）
```

## 配置项（application.properties）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `scheduling.min-cancel-lead-minutes` | `120` | 取消面试的最小提前量（分钟），不足则拒绝（`CANCEL_TOO_LATE`） |
| `scheduling.persistence-file` | `data/scheduling-state.json` | 状态快照文件路径 |
| `scheduling.persistence-enabled` | `true` | 是否启用快照持久化与重启恢复 |

## API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/candidates` | 注册候选人（初始阶段 APPLIED） |
| GET | `/candidates` / `/candidates/{id}` | 查询候选人 |
| POST | `/candidates/{id}/advance` | 推进阶段 `{targetStage, operatorId, operatorRole}` |
| POST | `/interviews/interviewers` | 注册面试官 |
| POST | `/availability/{ownerId}` | 添加可用时段 `{start, end}`（可带时区） |
| GET | `/availability/{ownerId}` | 查询可用时段 |
| DELETE | `/availability/{ownerId}` | 移除可用时段 |
| POST | `/interviews` | 安排面试（需 `requestId` 幂等键） |
| POST | `/interviews/{id}/reschedule` | 改期 `{newWindow, operatorId, operatorRole, reason}` |
| POST | `/interviews/{id}/cancel` | 取消 `{operatorId, operatorRole, reason}` |
| GET | `/interviews` / `/interviews/{id}` | 查询面试 |
| GET | `/audits` | 审计查询 |

错误响应统一为 `{timestamp, status, code, message}`；冲突响应为
`{success:false, conflict:true, message, alternatives:[{start,end}...]}`。

## 测试覆盖

- `StageTransitionTest`：阶段合法推进、越级/回退/原地/终态拒绝及原因说明。
- `InterviewSchedulingTest`：时区换算、首尾相接不误判、冲突与替代时段、
  幂等重提、改期原子性（失败保留旧时段）、取消提前量、已开始面试保护、越权。
- `ConcurrencyTest`：16 线程抢占同一时段仅 1 人成功；并发重复 `requestId`
  只产生一场面试。
- `PersistenceRecoveryTest`：快照后全新实例恢复，已确认面试、已占用时段、
  幂等索引与审计记录保持一致。
- `WebApiTest`：HTTP 层 200/400/403/409 语义与审计查询。
