package com.github.huchangjun118.interviewschedulingjava.tests;

import com.github.huchangjun118.interviewschedulingjava.ScheduleProperties;
import com.github.huchangjun118.interviewschedulingjava.domain.AuditAction;
import com.github.huchangjun118.interviewschedulingjava.domain.Interview;
import com.github.huchangjun118.interviewschedulingjava.domain.InterviewStatus;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import com.github.huchangjun118.interviewschedulingjava.dto.ScheduleRequest;
import com.github.huchangjun118.interviewschedulingjava.dto.ScheduleResult;
import com.github.huchangjun118.interviewschedulingjava.dto.TimeWindow;
import com.github.huchangjun118.interviewschedulingjava.service.AuditService;
import com.github.huchangjun118.interviewschedulingjava.service.AvailabilityService;
import com.github.huchangjun118.interviewschedulingjava.service.CandidateService;
import com.github.huchangjun118.interviewschedulingjava.service.InterviewService;
import com.github.huchangjun118.interviewschedulingjava.service.PermissionDeniedException;
import com.github.huchangjun118.interviewschedulingjava.service.PermissionService;
import com.github.huchangjun118.interviewschedulingjava.service.SchedulingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 排期核心场景：时区换算、首尾相接、冲突与替代时段、幂等、改期、取消、越权。
 */
class InterviewSchedulingTest {

    private AuditService auditService;
    private CandidateService candidateService;
    private AvailabilityService availabilityService;
    private InterviewService interviewService;
    private ScheduleProperties properties;

    private static final Instant BASE = Instant.now().plus(3, ChronoUnit.DAYS)
            .truncatedTo(ChronoUnit.HOURS);

    @BeforeEach
    void setUp() {
        auditService = new AuditService();
        candidateService = new CandidateService(auditService);
        availabilityService = new AvailabilityService();
        PermissionService permissionService = new PermissionService(auditService);
        properties = new ScheduleProperties();
        properties.setMinCancelLeadMinutes(120);
        interviewService = new InterviewService(candidateService, availabilityService,
                auditService, permissionService, properties);

        candidateService.register("cand1", "候选人一");
        candidateService.register("cand2", "候选人二");
        interviewService.registerInterviewer("int1", "面试官一");
        interviewService.registerInterviewer("int2", "面试官二");
        // 双方全天可用（UTC）
        availabilityService.addSlot("int1", BASE.minus(1, ChronoUnit.DAYS), BASE.plus(2, ChronoUnit.DAYS));
        availabilityService.addSlot("int2", BASE.minus(1, ChronoUnit.DAYS), BASE.plus(2, ChronoUnit.DAYS));
        availabilityService.addSlot("cand1", BASE.minus(1, ChronoUnit.DAYS), BASE.plus(2, ChronoUnit.DAYS));
        availabilityService.addSlot("cand2", BASE.minus(1, ChronoUnit.DAYS), BASE.plus(2, ChronoUnit.DAYS));
    }

    private ScheduleRequest request(String requestId, String candidateId, String interviewerId,
                                    Instant start, Instant end, String operatorRole) {
        ScheduleRequest req = new ScheduleRequest();
        req.setRequestId(requestId);
        req.setCandidateId(candidateId);
        req.setInterviewerId(interviewerId);
        req.setWindow(new TimeWindow(start.toString(), end.toString()));
        req.setOperatorId("rec1");
        req.setOperatorRole(operatorRole);
        return req;
    }

    @Test
    void scheduleSuccess() {
        ScheduleResult r = interviewService.schedule(request(
                "req-1", "cand1", "int1", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        assertTrue(r.isSuccess());
        assertEquals(InterviewStatus.SCHEDULED, r.getInterview().getStatus());
        assertEquals(1, interviewService.listAll().size());
    }

    @Test
    void timezoneConvertedToUtcBeforeConflictCheck() {
        // 面试官可用时段以 +08:00 声明，对应 UTC 01:00 ~ 09:00
        availabilityService.clear();
        availabilityService.addSlot("int1",
                TimeWindow.parseToUtc("2028-01-01T09:00:00+08:00"),
                TimeWindow.parseToUtc("2028-01-01T17:00:00+08:00"));
        availabilityService.addSlot("cand1",
                TimeWindow.parseToUtc("2028-01-01T00:00:00Z"),
                TimeWindow.parseToUtc("2028-01-01T23:59:00Z"));
        // 用 UTC 表达同一时段：02:00Z ~ 03:00Z 即北京时间 10:00 ~ 11:00
        ScheduleResult r = interviewService.schedule(request("req-tz", "cand1", "int1",
                TimeWindow.parseToUtc("2028-01-01T02:00:00Z"),
                TimeWindow.parseToUtc("2028-01-01T03:00:00Z"), "RECRUITER"));
        assertTrue(r.isSuccess(), "不同时区的时段应先换算到统一时间轴再判定");
    }

    @Test
    void adjacentSlotsDoNotConflict() {
        ScheduleResult first = interviewService.schedule(request(
                "req-a", "cand1", "int1", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        assertTrue(first.isSuccess());
        // 首尾相接：第一场结束即第二场开始，不得误判为冲突
        ScheduleResult second = interviewService.schedule(request("req-b", "cand2", "int1",
                BASE.plus(1, ChronoUnit.HOURS), BASE.plus(2, ChronoUnit.HOURS), "RECRUITER"));
        assertTrue(second.isSuccess(), "首尾相接的时段不得被误判为冲突");
    }

    @Test
    void overlappingSlotsConflictWithAlternatives() {
        interviewService.schedule(request(
                "req-c", "cand1", "int1", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        ScheduleResult conflict = interviewService.schedule(request("req-d", "cand2", "int1",
                BASE.plus(30, ChronoUnit.MINUTES), BASE.plus(90, ChronoUnit.MINUTES), "RECRUITER"));
        assertFalse(conflict.isSuccess());
        assertTrue(conflict.isConflict());
        assertTrue(conflict.getMessage().contains("int1"));
        assertFalse(conflict.getAlternatives().isEmpty(), "冲突时应给出替代时段");
        // 替代时段必须确实可占用
        ScheduleResult.TimeWindowView alt = conflict.getAlternatives().get(0);
        ScheduleResult retry = interviewService.schedule(request(
                "req-d2", "cand2", "int1", alt.start(), alt.end(), "RECRUITER"));
        assertTrue(retry.isSuccess());
    }

    @Test
    void candidateDoubleBookingRejected() {
        interviewService.schedule(request(
                "req-e", "cand1", "int1", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        ScheduleResult r = interviewService.schedule(request(
                "req-f", "cand1", "int2", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        assertTrue(r.isConflict());
        assertTrue(r.getMessage().contains("cand1"));
    }

    @Test
    void idempotentResubmissionReturnsSameInterview() {
        ScheduleRequest req = request(
                "req-idem", "cand1", "int1", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER");
        ScheduleResult first = interviewService.schedule(req);
        ScheduleResult second = interviewService.schedule(req);
        assertTrue(second.isSuccess());
        assertEquals(first.getInterview().getId(), second.getInterview().getId());
        assertEquals(1, interviewService.listAll().size(), "重复提交不得产生重复面试");
    }

    @Test
    void rescheduleMovesWindowAtomically() {
        ScheduleResult scheduled = interviewService.schedule(request(
                "req-g", "cand1", "int1", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        String id = scheduled.getInterview().getId();
        Instant newStart = BASE.plus(4, ChronoUnit.HOURS);
        ScheduleResult r = interviewService.reschedule(id, newStart,
                newStart.plus(1, ChronoUnit.HOURS), "rec1", Role.RECRUITER, "面试官临时有事");
        assertTrue(r.isSuccess());
        assertEquals(InterviewStatus.RESCHEDULED, r.getInterview().getStatus());
        assertEquals(newStart, r.getInterview().getStart());
        // 旧时段已释放：他人可占用
        ScheduleResult reuse = interviewService.schedule(request(
                "req-h", "cand2", "int1", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        assertTrue(reuse.isSuccess(), "改期后旧时段应被释放");
        // 新时段已占用：他人不可占用
        ScheduleResult clash = interviewService.schedule(request("req-i", "cand2", "int1",
                newStart, newStart.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        assertTrue(clash.isConflict(), "改期后新时段应被占用");
    }

    @Test
    void failedRescheduleKeepsOldWindow() {
        interviewService.schedule(request(
                "req-j", "cand2", "int1", BASE.plus(2, ChronoUnit.HOURS),
                BASE.plus(3, ChronoUnit.HOURS), "RECRUITER"));
        ScheduleResult scheduled = interviewService.schedule(request(
                "req-k", "cand1", "int1", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        String id = scheduled.getInterview().getId();
        // 尝试改期到已被占用的时段 -> 冲突，旧时段必须保持占用
        ScheduleResult r = interviewService.reschedule(id, BASE.plus(2, ChronoUnit.HOURS),
                BASE.plus(3, ChronoUnit.HOURS), "rec1", Role.RECRUITER, null);
        assertTrue(r.isConflict());
        Interview unchanged = interviewService.get(id);
        assertEquals(BASE, unchanged.getStart(), "改期失败不得释放旧时段");
        assertEquals(InterviewStatus.SCHEDULED, unchanged.getStatus());
    }

    @Test
    void cancelWithinLeadTimeRejected() {
        Instant soon = Instant.now().plus(30, ChronoUnit.MINUTES);
        availabilityService.addSlot("int1", soon.minus(5, ChronoUnit.MINUTES), soon.plus(2, ChronoUnit.HOURS));
        availabilityService.addSlot("cand1", soon.minus(5, ChronoUnit.MINUTES), soon.plus(2, ChronoUnit.HOURS));
        ScheduleResult scheduled = interviewService.schedule(request(
                "req-l", "cand1", "int1", soon, soon.plus(30, ChronoUnit.MINUTES), "RECRUITER"));
        String id = scheduled.getInterview().getId();
        SchedulingException e = assertThrows(SchedulingException.class,
                () -> interviewService.cancel(id, "rec1", Role.RECRUITER, "计划变更"));
        assertEquals("CANCEL_TOO_LATE", e.getCode());
        assertEquals(1, auditService.listByTarget(id).stream()
                .filter(a -> a.getAction() == AuditAction.CANCEL_REJECTED).count());
    }

    @Test
    void cancelSuccessReleasesSlot() {
        ScheduleResult scheduled = interviewService.schedule(request(
                "req-m", "cand1", "int1", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        String id = scheduled.getInterview().getId();
        Interview cancelled = interviewService.cancel(id, "rec1", Role.RECRUITER, "候选人放弃");
        assertEquals(InterviewStatus.CANCELLED, cancelled.getStatus());
        ScheduleResult reuse = interviewService.schedule(request(
                "req-n", "cand2", "int1", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        assertTrue(reuse.isSuccess(), "取消后时段应被释放");
    }

    @Test
    void startedInterviewCannotBeRescheduledOrCancelled() {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        availabilityService.addSlot("int1", past.minus(1, ChronoUnit.HOURS), past.plus(3, ChronoUnit.HOURS));
        availabilityService.addSlot("cand1", past.minus(1, ChronoUnit.HOURS), past.plus(3, ChronoUnit.HOURS));
        ScheduleResult scheduled = interviewService.schedule(request(
                "req-o", "cand1", "int1", past, past.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        String id = scheduled.getInterview().getId();
        SchedulingException e1 = assertThrows(SchedulingException.class, () ->
                interviewService.reschedule(id, BASE, BASE.plus(1, ChronoUnit.HOURS),
                        "rec1", Role.RECRUITER, null));
        assertEquals("INTERVIEW_ALREADY_STARTED", e1.getCode());
        SchedulingException e2 = assertThrows(SchedulingException.class,
                () -> interviewService.cancel(id, "rec1", Role.RECRUITER, null));
        assertEquals("INTERVIEW_ALREADY_STARTED", e2.getCode());
    }

    @Test
    void candidateCannotScheduleAndDenialIsAudited() {
        PermissionDeniedException e = assertThrows(PermissionDeniedException.class,
                () -> interviewService.schedule(request(
                        "req-p", "cand1", "int1", BASE, BASE.plus(1, ChronoUnit.HOURS), "CANDIDATE")));
        assertTrue(e.getMessage().contains("CANDIDATE"));
        assertEquals(1, auditService.listAll().stream()
                .filter(a -> a.getAction() == AuditAction.PERMISSION_DENIED)
                .filter(a -> "rec1".equals(a.getOperatorId())).count(),
                "越权请求必须记录操作人与被拒原因");
    }

    @Test
    void candidateCanCancelOwnButNotOthers() {
        ScheduleResult scheduled = interviewService.schedule(request(
                "req-q", "cand1", "int1", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        String id = scheduled.getInterview().getId();
        assertThrows(PermissionDeniedException.class,
                () -> interviewService.cancel(id, "cand2", Role.CANDIDATE, null));
        Interview cancelled = interviewService.cancel(id, "cand1", Role.CANDIDATE, "时间不合适");
        assertEquals(InterviewStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void candidateCannotReschedule() {
        ScheduleResult scheduled = interviewService.schedule(request(
                "req-r", "cand1", "int1", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        assertThrows(PermissionDeniedException.class, () ->
                interviewService.reschedule(scheduled.getInterview().getId(),
                        BASE.plus(5, ChronoUnit.HOURS), BASE.plus(6, ChronoUnit.HOURS),
                        "cand1", Role.CANDIDATE, null));
    }

    @Test
    void missingAvailabilityRejected() {
        availabilityService.clear();
        availabilityService.addSlot("int1", BASE, BASE.plus(4, ChronoUnit.HOURS));
        // 候选人无任何可用时段
        ScheduleResult r = interviewService.schedule(request(
                "req-s", "cand1", "int1", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        assertTrue(r.isConflict());
        assertTrue(r.getMessage().contains("cand1"));
    }

    @Test
    void differentInterviewsDoNotInterfere() {
        ScheduleResult a = interviewService.schedule(request(
                "req-t", "cand1", "int1", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        ScheduleResult b = interviewService.schedule(request(
                "req-u", "cand2", "int2", BASE, BASE.plus(1, ChronoUnit.HOURS), "RECRUITER"));
        assertTrue(a.isSuccess() && b.isSuccess());
        assertNotEquals(a.getInterview().getId(), b.getInterview().getId());
    }
}
