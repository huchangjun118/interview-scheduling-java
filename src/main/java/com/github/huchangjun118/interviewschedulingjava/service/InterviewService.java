package com.github.huchangjun118.interviewschedulingjava.service;

import com.github.huchangjun118.interviewschedulingjava.ScheduleProperties;
import com.github.huchangjun118.interviewschedulingjava.domain.AuditAction;
import com.github.huchangjun118.interviewschedulingjava.domain.Interview;
import com.github.huchangjun118.interviewschedulingjava.domain.InterviewStatus;
import com.github.huchangjun118.interviewschedulingjava.domain.Interviewer;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import com.github.huchangjun118.interviewschedulingjava.dto.ScheduleRequest;
import com.github.huchangjun118.interviewschedulingjava.dto.ScheduleResult;
import com.github.huchangjun118.interviewschedulingjava.dto.ScheduleResult.TimeWindowView;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 面试排期核心服务。
 * 所有占用/释放时段的判定都在同一把锁内完成，保证并发下同一时段只会被一方占用；
 * 改期在同一把锁内先校验新时段再一次性切换，不会留下已释放未占用的空档。
 */
@Service
public class InterviewService {

    private static final Set<Role> SCHEDULE_ROLES = EnumSet.of(Role.RECRUITER, Role.ADMIN);
    private static final Set<Role> RESCHEDULE_ROLES = EnumSet.of(Role.RECRUITER, Role.ADMIN);
    private static final Set<Role> CANCEL_ROLES = EnumSet.of(Role.RECRUITER, Role.ADMIN, Role.CANDIDATE);
    private static final Set<InterviewStatus> ACTIVE_STATUSES =
            EnumSet.of(InterviewStatus.SCHEDULED, InterviewStatus.RESCHEDULED);

    private final Map<String, Interview> interviews = new ConcurrentHashMap<>();
    private final Map<String, String> requestIdIndex = new ConcurrentHashMap<>();
    private final Map<String, Interviewer> interviewers = new ConcurrentHashMap<>();
    private final ReentrantLock bookingLock = new ReentrantLock();

    private final CandidateService candidateService;
    private final AvailabilityService availabilityService;
    private final AuditService auditService;
    private final PermissionService permissionService;
    private final ScheduleProperties properties;

    public InterviewService(CandidateService candidateService,
                            AvailabilityService availabilityService,
                            AuditService auditService,
                            PermissionService permissionService,
                            ScheduleProperties properties) {
        this.candidateService = candidateService;
        this.availabilityService = availabilityService;
        this.auditService = auditService;
        this.permissionService = permissionService;
        this.properties = properties;
    }

    public Interviewer registerInterviewer(String id, String name) {
        Interviewer i = new Interviewer(id, name);
        interviewers.put(id, i);
        return i;
    }

    public Interview get(String id) {
        Interview i = interviews.get(id);
        if (i == null) {
            throw new SchedulingException("INTERVIEW_NOT_FOUND", "面试不存在: " + id);
        }
        return i;
    }

    public List<Interview> listAll() {
        return new ArrayList<>(interviews.values());
    }

    /**
     * 安排面试。幂等：同一 requestId 重复提交返回首次结果，不产生重复面试。
     */
    public ScheduleResult schedule(ScheduleRequest req) {
        Role role = permissionService.parseRole(req.getOperatorRole());
        permissionService.require(req.getOperatorId(), role, SCHEDULE_ROLES, "安排面试", req.getCandidateId());

        if (req.getRequestId() == null || req.getRequestId().isBlank()) {
            throw new SchedulingException("MISSING_REQUEST_ID", "缺少幂等请求标识 requestId");
        }

        bookingLock.lock();
        try {
            String existingId = requestIdIndex.get(req.getRequestId());
            if (existingId != null) {
                return ScheduleResult.ok(interviews.get(existingId), "重复请求，返回首次安排结果");
            }
            candidateService.get(req.getCandidateId());
            if (!interviewers.containsKey(req.getInterviewerId())) {
                throw new SchedulingException("INTERVIEWER_NOT_FOUND", "面试官不存在: " + req.getInterviewerId());
            }
            Instant start = req.getWindow().startUtc();
            Instant end = req.getWindow().endUtc();
            validateWindow(start, end);

            String conflictReason = checkBookable(req.getCandidateId(), req.getInterviewerId(), start, end, null);
            if (conflictReason != null) {
                List<TimeWindowView> alternatives = suggestAlternatives(
                        req.getCandidateId(), req.getInterviewerId(), start, end);
                auditService.record(AuditAction.SCHEDULE_CONFLICT, req.getOperatorId(), role,
                        req.getCandidateId(), conflictReason);
                return ScheduleResult.conflict(conflictReason, alternatives);
            }

            Interview interview = new Interview();
            interview.setId(UUID.randomUUID().toString());
            interview.setRequestId(req.getRequestId());
            interview.setCandidateId(req.getCandidateId());
            interview.setInterviewerId(req.getInterviewerId());
            interview.setStart(start);
            interview.setEnd(end);
            interview.setStatus(InterviewStatus.SCHEDULED);
            interview.setVersion(1);
            interview.setCreatedAt(Instant.now());
            interview.setUpdatedAt(interview.getCreatedAt());
            interviews.put(interview.getId(), interview);
            requestIdIndex.put(req.getRequestId(), interview.getId());
            auditService.record(AuditAction.SCHEDULE, req.getOperatorId(), role,
                    interview.getId(), "安排面试 " + start + " ~ " + end);
            return ScheduleResult.ok(interview, "安排成功");
        } finally {
            bookingLock.unlock();
        }
    }

    /**
     * 改期：在同一把锁内原子地完成"释放旧时段 + 占用新时段"。
     */
    public ScheduleResult reschedule(String interviewId, Instant newStart, Instant newEnd,
                                     String operatorId, Role role, String reason) {
        permissionService.require(operatorId, role, RESCHEDULE_ROLES, "改期", interviewId);
        bookingLock.lock();
        try {
            Interview interview = get(interviewId);
            ensureActive(interview, "改期");
            ensureNotStarted(interview, "改期");
            validateWindow(newStart, newEnd);

            String conflictReason = checkBookable(interview.getCandidateId(), interview.getInterviewerId(),
                    newStart, newEnd, interview.getId());
            if (conflictReason != null) {
                List<TimeWindowView> alternatives = suggestAlternatives(
                        interview.getCandidateId(), interview.getInterviewerId(), newStart, newEnd);
                auditService.record(AuditAction.SCHEDULE_CONFLICT, operatorId, role,
                        interviewId, "改期冲突: " + conflictReason);
                return ScheduleResult.conflict(conflictReason, alternatives);
            }
            // 原子切换：旧时段在此刻释放，新时段在此刻占用，中间无空档
            interview.setStart(newStart);
            interview.setEnd(newEnd);
            interview.setStatus(InterviewStatus.RESCHEDULED);
            interview.setVersion(interview.getVersion() + 1);
            interview.setUpdatedAt(Instant.now());
            auditService.record(AuditAction.RESCHEDULE, operatorId, role, interviewId,
                    "改期到 " + newStart + " ~ " + newEnd + (reason == null ? "" : "，原因: " + reason));
            return ScheduleResult.ok(interview, "改期成功");
        } finally {
            bookingLock.unlock();
        }
    }

    /**
     * 取消：需满足最小提前量，已开始或已结束的面试不得取消。
     */
    public Interview cancel(String interviewId, String operatorId, Role role, String reason) {
        permissionService.require(operatorId, role, CANCEL_ROLES, "取消面试", interviewId);
        bookingLock.lock();
        try {
            Interview interview = get(interviewId);
            if (role == Role.CANDIDATE && !interview.getCandidateId().equals(operatorId)) {
                String msg = "候选人只能取消自己的面试";
                auditService.record(AuditAction.PERMISSION_DENIED, operatorId, role, interviewId, msg);
                throw new PermissionDeniedException(msg);
            }
            ensureActive(interview, "取消");
            ensureNotStarted(interview, "取消");
            Instant now = Instant.now();
            Instant deadline = interview.getStart()
                    .minus(Duration.ofMinutes(properties.getMinCancelLeadMinutes()));
            if (now.isAfter(deadline)) {
                String msg = "距面试开始不足最小提前量 " + properties.getMinCancelLeadMinutes()
                        + " 分钟，取消被拒绝";
                auditService.record(AuditAction.CANCEL_REJECTED, operatorId, role, interviewId, msg);
                throw new SchedulingException("CANCEL_TOO_LATE", msg);
            }
            interview.setStatus(InterviewStatus.CANCELLED);
            interview.setVersion(interview.getVersion() + 1);
            interview.setUpdatedAt(now);
            auditService.record(AuditAction.CANCEL, operatorId, role, interviewId,
                    "取消面试" + (reason == null ? "" : "，原因: " + reason));
            return interview;
        } finally {
            bookingLock.unlock();
        }
    }

    /**
     * 校验时段可占用：双方可用时段覆盖 + 双方无重叠面试。返回 null 表示可占用。
     */
    private String checkBookable(String candidateId, String interviewerId,
                                 Instant start, Instant end, String excludeInterviewId) {
        if (!availabilityService.isAvailable(interviewerId, start, end)) {
            return "面试官 " + interviewerId + " 在 " + start + " ~ " + end + " 无可用时段";
        }
        if (!availabilityService.isAvailable(candidateId, start, end)) {
            return "候选人 " + candidateId + " 在 " + start + " ~ " + end + " 无可用时段";
        }
        for (Interview other : interviews.values()) {
            if (!ACTIVE_STATUSES.contains(other.getStatus())) {
                continue;
            }
            if (other.getId().equals(excludeInterviewId)) {
                continue;
            }
            boolean overlaps = AvailabilityService.overlaps(start, end, other.getStart(), other.getEnd());
            if (!overlaps) {
                continue;
            }
            if (other.getInterviewerId().equals(interviewerId)) {
                return "面试官 " + interviewerId + " 在同时段已有面试 " + other.getId();
            }
            if (other.getCandidateId().equals(candidateId)) {
                return "候选人 " + candidateId + " 在同时段已有面试 " + other.getId();
            }
        }
        return null;
    }

    /**
     * 冲突时推荐替代时段：从原开始时间起按 30 分钟步进向后搜索双方都可行的时间段。
     */
    private List<TimeWindowView> suggestAlternatives(String candidateId, String interviewerId,
                                                     Instant start, Instant end) {
        List<TimeWindowView> result = new ArrayList<>();
        Duration duration = Duration.between(start, end);
        Duration step = Duration.ofMinutes(30);
        Instant cursor = start.plus(step);
        Instant horizon = start.plus(Duration.ofDays(7));
        while (result.size() < 3 && cursor.isBefore(horizon)) {
            Instant candidateEnd = cursor.plus(duration);
            if (checkBookable(candidateId, interviewerId, cursor, candidateEnd, null) == null) {
                result.add(new TimeWindowView(cursor, candidateEnd));
            }
            cursor = cursor.plus(step);
        }
        return result;
    }

    private void ensureActive(Interview interview, String action) {
        if (!ACTIVE_STATUSES.contains(interview.getStatus())) {
            throw new SchedulingException("INTERVIEW_NOT_ACTIVE",
                    "面试状态为 " + interview.getStatus() + "，不允许" + action);
        }
    }

    private void ensureNotStarted(Interview interview, String action) {
        Instant now = Instant.now();
        if (!now.isBefore(interview.getStart())) {
            throw new SchedulingException("INTERVIEW_ALREADY_STARTED",
                    "面试已开始或已结束，不允许" + action);
        }
    }

    private void validateWindow(Instant start, Instant end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new SchedulingException("INVALID_WINDOW", "时段非法：开始必须早于结束");
        }
    }

    /**
     * 供持久化恢复使用：恢复后已确认面试与其占用时段自然一致（冲突判定完全由面试集合推导）。
     */
    public void restore(List<Interview> restoredInterviews, List<Interviewer> restoredInterviewers) {
        interviews.clear();
        requestIdIndex.clear();
        interviewers.clear();
        for (Interview i : restoredInterviews) {
            interviews.put(i.getId(), i);
            if (i.getRequestId() != null) {
                requestIdIndex.put(i.getRequestId(), i.getId());
            }
        }
        for (Interviewer i : restoredInterviewers) {
            interviewers.put(i.getId(), i);
        }
    }

    public List<Interviewer> listInterviewers() {
        return new ArrayList<>(interviewers.values());
    }

    public void clear() {
        interviews.clear();
        requestIdIndex.clear();
        interviewers.clear();
    }
}
