package com.github.huchangjun118.interviewschedulingjava.service;

import com.github.huchangjun118.interviewschedulingjava.config.SchedulingProperties;
import com.github.huchangjun118.interviewschedulingjava.domain.Candidate;
import com.github.huchangjun118.interviewschedulingjava.domain.Interview;
import com.github.huchangjun118.interviewschedulingjava.domain.InterviewStatus;
import com.github.huchangjun118.interviewschedulingjava.domain.RecruitmentStage;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import com.github.huchangjun118.interviewschedulingjava.domain.TimeWindow;
import com.github.huchangjun118.interviewschedulingjava.exception.InvalidOperationException;
import com.github.huchangjun118.interviewschedulingjava.exception.PermissionDeniedException;
import com.github.huchangjun118.interviewschedulingjava.exception.ResourceNotFoundException;
import com.github.huchangjun118.interviewschedulingjava.exception.SchedulingConflictException;
import com.github.huchangjun118.interviewschedulingjava.store.SchedulingStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 面试排期、改期、取消服务。
 * 所有变更在 store 的互斥锁内完成，保证并发抢占同一时段时只有一个成功。
 */
@Service
public class SchedulingService {

    private final SchedulingStore store;
    private final AvailabilityService availabilityService;
    private final AuditService auditService;
    private final SchedulingProperties properties;
    private final Clock clock;

    public SchedulingService(SchedulingStore store, AvailabilityService availabilityService,
                             AuditService auditService, SchedulingProperties properties, Clock clock) {
        this.store = store;
        this.availabilityService = availabilityService;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    /** 安排面试，幂等：相同 requestKey 重复提交返回同一场面试。 */
    public Interview schedule(String requestKey, String operatorId, Role operatorRole,
                              String candidateId, String interviewerId, RecruitmentStage stage,
                              ZonedDateTime start, ZonedDateTime end) {
        // 幂等：同一请求键直接返回已有结果
        if (requestKey != null) {
            var existing = store.findInterviewByRequestKey(requestKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        checkSchedulePermission(operatorId, operatorRole, interviewerId);
        TimeWindow window = availabilityService.toUtcWindow(start, end);
        return store.mutate(() -> {
            // 双检：锁内再次检查幂等键，防止并发下重复创建
            if (requestKey != null) {
                var existing = store.findInterviewByRequestKey(requestKey);
                if (existing.isPresent()) {
                    return existing.get();
                }
            }
            Candidate candidate = store.candidates().get(candidateId);
            if (candidate == null) {
                throw new ResourceNotFoundException("候选人不存在: " + candidateId);
            }
            if (candidate.getStage() != stage) {
                throw new InvalidOperationException("面试阶段 " + stage
                        + " 与候选人当前阶段 " + candidate.getStage() + " 不一致");
            }
            assertSlotFree(candidateId, interviewerId, window, null);
            Instant now = Instant.now(clock);
            Interview interview = new Interview();
            interview.setId(UUID.randomUUID().toString());
            interview.setRequestKey(requestKey);
            interview.setCandidateId(candidateId);
            interview.setInterviewerId(interviewerId);
            interview.setStage(stage);
            interview.setWindow(window);
            interview.setStatus(InterviewStatus.SCHEDULED);
            interview.setCreatedAt(now);
            interview.setUpdatedAt(now);
            interview.setVersion(1);
            store.interviews().put(interview.getId(), interview);
            if (requestKey != null) {
                store.requestKeyToInterviewId().put(requestKey, interview.getId());
            }
            auditService.record(operatorId, operatorRole, "SCHEDULE", "INTERVIEW", interview.getId(),
                    true, "candidate=" + candidateId + " interviewer=" + interviewerId + " " + window);
            return interview;
        });
    }

    /** 改期：原子地释放旧时段并占用新时段。 */
    public Interview reschedule(String operatorId, Role operatorRole, String interviewId,
                                ZonedDateTime newStart, ZonedDateTime newEnd) {
        TimeWindow newWindow = availabilityService.toUtcWindow(newStart, newEnd);
        return store.mutate(() -> {
            Interview interview = mustGet(interviewId);
            checkInterviewPermission(operatorId, operatorRole, interview, "RESCHEDULE");
            ensureScheduledAndNotStarted(interview, "改期");
            // 校验通过前不触碰旧时段；校验与切换在同一锁内完成，对外无中间态
            assertSlotFree(interview.getCandidateId(), interview.getInterviewerId(), newWindow, interviewId);
            TimeWindow oldWindow = interview.getWindow();
            interview.setWindow(newWindow);
            interview.setUpdatedAt(Instant.now(clock));
            interview.setVersion(interview.getVersion() + 1);
            auditService.record(operatorId, operatorRole, "RESCHEDULE", "INTERVIEW", interviewId,
                    true, oldWindow + " -> " + newWindow);
            return interview;
        });
    }

    /** 取消：需满足最小提前量，已开始/已结束的面试不得取消。 */
    public Interview cancel(String operatorId, Role operatorRole, String interviewId, String reason) {
        return store.mutate(() -> {
            Interview interview = mustGet(interviewId);
            checkInterviewPermission(operatorId, operatorRole, interview, "CANCEL");
            ensureScheduledAndNotStarted(interview, "取消");
            Instant now = Instant.now(clock);
            Duration lead = Duration.between(now, interview.getWindow().getStart());
            if (lead.compareTo(properties.getMinCancelLeadTime()) < 0) {
                String msg = "距面试开始不足最小提前量 " + properties.getMinCancelLeadTime()
                        + "（剩余 " + lead + "），不允许取消";
                auditService.record(operatorId, operatorRole, "CANCEL", "INTERVIEW", interviewId, false, msg);
                throw new InvalidOperationException(msg);
            }
            interview.setStatus(InterviewStatus.CANCELLED);
            interview.setUpdatedAt(now);
            interview.setVersion(interview.getVersion() + 1);
            auditService.record(operatorId, operatorRole, "CANCEL", "INTERVIEW", interviewId,
                    true, reason);
            return interview;
        });
    }

    public Interview get(String interviewId) {
        return mustGet(interviewId);
    }

    public List<Interview> list() {
        return store.interviews().values().stream()
                .sorted(Comparator.comparing(Interview::getCreatedAt))
                .toList();
    }

    private Interview mustGet(String interviewId) {
        Interview interview = store.interviews().get(interviewId);
        if (interview == null) {
            throw new ResourceNotFoundException("面试不存在: " + interviewId);
        }
        return interview;
    }

    /** 校验时段可用：双方均未占用且都登记了覆盖该时段的可用时间。 */
    private void assertSlotFree(String candidateId, String interviewerId, TimeWindow window,
                                String excludeInterviewId) {
        List<String> problems = new ArrayList<>();
        if (availabilityService.isBooked(interviewerId, window, excludeInterviewId)) {
            problems.add("面试官 " + interviewerId + " 在 " + window + " 已有其他面试");
        }
        if (availabilityService.isBooked(candidateId, window, excludeInterviewId)) {
            problems.add("候选人 " + candidateId + " 在 " + window + " 已有其他面试");
        }
        if (!availabilityService.isAvailable(interviewerId, window)) {
            problems.add("面试官 " + interviewerId + " 未登记覆盖 " + window + " 的可用时段");
        }
        if (!availabilityService.isAvailable(candidateId, window)) {
            problems.add("候选人 " + candidateId + " 未登记覆盖 " + window + " 的可用时段");
        }
        if (!problems.isEmpty()) {
            List<TimeWindow> alternatives = findAlternatives(candidateId, interviewerId,
                    Duration.between(window.getStart(), window.getEnd()), window.getStart(), excludeInterviewId);
            throw new SchedulingConflictException(String.join("；", problems), alternatives);
        }
    }

    /** 从请求时点起按 30 分钟步长向前搜索双方均可行的替代时段。 */
    private List<TimeWindow> findAlternatives(String candidateId, String interviewerId,
                                              Duration duration, Instant from, String excludeInterviewId) {
        List<TimeWindow> alternatives = new ArrayList<>();
        Instant limit = from.plus(properties.getAlternativeSearchDays(), ChronoUnit.DAYS);
        Instant cursor = from;
        while (alternatives.size() < properties.getMaxAlternatives()
                && cursor.plus(duration).compareTo(limit) <= 0) {
            cursor = cursor.plus(30, ChronoUnit.MINUTES);
            TimeWindow candidateWindow = new TimeWindow(cursor, cursor.plus(duration));
            if (!availabilityService.isBooked(candidateId, candidateWindow, excludeInterviewId)
                    && !availabilityService.isBooked(interviewerId, candidateWindow, excludeInterviewId)
                    && availabilityService.isAvailable(candidateId, candidateWindow)
                    && availabilityService.isAvailable(interviewerId, candidateWindow)) {
                alternatives.add(candidateWindow);
            }
        }
        return alternatives;
    }

    private void checkSchedulePermission(String operatorId, Role operatorRole, String interviewerId) {
        if (operatorRole == Role.ADMIN) {
            return;
        }
        if (operatorRole == Role.RECRUITER && operatorId.equals(interviewerId)) {
            return;
        }
        String reason = "角色 " + operatorRole + " 的操作人 " + operatorId
                + " 无权为面试官 " + interviewerId + " 安排面试";
        auditService.record(operatorId, operatorRole, "SCHEDULE", "INTERVIEW", interviewerId, false, reason);
        throw new PermissionDeniedException(reason);
    }

    private void checkInterviewPermission(String operatorId, Role operatorRole, Interview interview, String action) {
        if (operatorRole == Role.ADMIN) {
            return;
        }
        if (operatorRole == Role.RECRUITER && operatorId.equals(interview.getInterviewerId())) {
            return;
        }
        String reason = "角色 " + operatorRole + " 的操作人 " + operatorId
                + " 无权对面试 " + interview.getId() + " 执行 " + action;
        auditService.record(operatorId, operatorRole, action, "INTERVIEW", interview.getId(), false, reason);
        throw new PermissionDeniedException(reason);
    }

    private void ensureScheduledAndNotStarted(Interview interview, String operation) {
        if (interview.getStatus() != InterviewStatus.SCHEDULED) {
            throw new InvalidOperationException("面试 " + interview.getId() + " 当前状态为 "
                    + interview.getStatus() + "，不允许" + operation);
        }
        Instant now = Instant.now(clock);
        if (!now.isBefore(interview.getWindow().getStart())) {
            throw new InvalidOperationException("面试 " + interview.getId() + " 已开始或已结束，不允许" + operation);
        }
    }
}
