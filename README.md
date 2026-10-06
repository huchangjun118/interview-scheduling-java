# 招聘面试阶段流转与排期服务

基于 Spring Boot 的招聘面试排期系统。候选人在既定招聘阶段间顺序推进，面试安排到
招聘官（面试官）与候选人都可行的时段；所有约束均由服务端保证，不接入外部日历或邮件服务。

## 构建与运行

```bash
mvn compile        # 编译
mvn test           # 运行全部自动化测试
mvn spring-boot:run
```

## 领域语义

### 招聘阶段（RecruitmentStage）

固定顺序：`APPLIED → SCREENING → PHONE_INTERVIEW → TECHNICAL_INTERVIEW → ONSITE_INTERVIEW → OFFER → HIRED`

- 只能单步向前推进；回退、原地重复、越级跳转均被拒绝（HTTP 409），
  响应体携带 `currentStage`、`targetStage` 与中文冲突说明。
- `HIRED` 为终态，不可再流转。
- 安排面试时，面试的 `stage` 必须与候选人当前阶段一致。

### 面试状态（InterviewStatus）

| 状态 | 含义 | 允许的后续操作 |
|---|---|---|
| `SCHEDULED` | 已确认排期，时段被占用 | 改期、取消（均需未开始） |
| `COMPLETED` | 已完成（预留） | 无 |
| `CANCELLED` | 已取消，时段自动释放 | 无 |

- 已开始（当前时间 ≥ 开始时间）或已结束的面试不得改期或取消。
- 取消要求距开始时间不小于最小提前量（`scheduling.min-cancel-lead-time`，默认 2 小时）。

### 时段与冲突

- 所有时段在服务端统一换算到 UTC（`Instant`）时间轴后存储与判定，请求可携带任意时区
  （如 `2030-01-01T18:00:00+08:00`）。
- 时段为左闭右开区间 `[start, end)`，**首尾相接不算冲突**（10:00-11:00 与 11:00-12:00 可背靠背）。
- 同一面试官或同一候选人在同一时段只能有一场 `SCHEDULED` 面试。
- 排期要求面试官与候选人都登记了**完全覆盖**面试时段的可用时段。

### 并发与原子性

- 所有排期变更在同一互斥锁内完成"校验 + 占用"：并发抢占同一时段时只有一个请求成功，
  失败方收到 409 与**替代时段列表**（`alternatives`）。
- 改期在同一临界区内先校验新时段、再切换：任何时刻对外要么占旧时段、要么占新时段，
  不存在"已释放却未占用"的中间态；改期失败则旧时段保持占用。

### 幂等与恢复

- 排期请求携带 `requestKey`：重复提交（含并发重复）返回同一场面试，不会产生重复记录。
- 每次变更后状态快照原子落盘（临时文件 + 原子移动）到 `scheduling.storage-file`；
  服务重启后从快照恢复，已确认面试、占用时段、幂等键、候选人阶段、可用时段与审计记录保持一致，
  不会出现"时段被占用却查不到面试"的状态。

### 角色权限

| 操作 | RECRUITER（招聘官） | CANDIDATE（候选人） | ADMIN（管理员） |
|---|---|---|---|
| 推进候选人阶段 | ✅ | ❌ | ✅ |
| 安排面试 | ✅（仅自己作为面试官） | ❌ | ✅ |
| 改期 / 取消 | ✅（仅自己的面试） | ❌ | ✅ |
| 维护可用时段 | ✅（仅本人） | ✅（仅本人） | ✅ |

越权请求返回 403，并以 `allowed=false` 记录审计（操作人、角色、动作、目标、被拒原因）。

### 审计

每一次安排、改期、取消、阶段流转（含被拒绝的尝试）都会记录：操作人、角色、动作、
目标对象、时间、是否允许与原因。通过 `GET /api/audit` 查询。

## REST API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/candidates` | 注册候选人 `{candidateId, name}` |
| GET | `/api/candidates` / `/api/candidates/{id}` | 查询候选人 |
| POST | `/api/candidates/{id}/stage` | 阶段流转 `{operatorId, operatorRole, targetStage}` |
| POST | `/api/availability` | 登记可用时段 `{operatorId, operatorRole, ownerId, start, end}`（带时区） |
| DELETE | `/api/availability/{slotId}` | 删除可用时段 `{operatorId, operatorRole}` |
| GET | `/api/availability/{ownerId}` | 查询某人的可用时段 |
| POST | `/api/interviews` | 安排面试 `{requestKey, operatorId, operatorRole, candidateId, interviewerId, stage, start, end}` |
| POST | `/api/interviews/{id}/reschedule` | 改期 `{operatorId, operatorRole, start, end}` |
| POST | `/api/interviews/{id}/cancel` | 取消 `{operatorId, operatorRole, reason}` |
| GET | `/api/interviews` / `/api/interviews/{id}` | 查询面试 |
| GET | `/api/audit` | 查询审计记录 |

错误响应统一为 `{code, message}`；冲突响应额外携带 `alternatives: [{start, end}]`（UTC）。
`code` 取值：`STAGE_TRANSITION_REJECTED`、`SCHEDULING_CONFLICT`、`PERMISSION_DENIED`、
`NOT_FOUND`、`INVALID_OPERATION`、`BAD_REQUEST`。

## 配置项（application.properties）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `scheduling.min-cancel-lead-time` | `PT2H` | 取消的最小提前量（ISO-8601 Duration） |
| `scheduling.storage-file` | `data/scheduling-state.json` | 状态快照文件，重启后从此恢复 |
| `scheduling.max-alternatives` | `3` | 冲突时返回的替代时段数量上限 |
| `scheduling.alternative-search-days` | `7` | 替代时段自请求时点起向前搜索的天数 |

## 测试覆盖

- `StageServiceTest`：顺序推进、越级/回退/原地拒绝、候选人越权与审计。
- `AvailabilityConflictTest`：时区换算、首尾相接不冲突、重叠冲突与替代时段、跨时区冲突。
- `ConcurrencyIdempotencyTest`：并发抢占同一时段仅一人成功、并发同幂等键只建一场、重复提交幂等。
- `RescheduleCancelTest`：改期原子释放/占用、改期失败保留旧时段、最小提前量、已开始/已结束保护。
- `PermissionRecoveryTest`：三类角色越权拒绝与审计、重启后状态一致恢复、占用时段可追溯。
- `WebApiTest`：REST 端到端（排期、409 冲突响应、403 越权、审计查询）。
