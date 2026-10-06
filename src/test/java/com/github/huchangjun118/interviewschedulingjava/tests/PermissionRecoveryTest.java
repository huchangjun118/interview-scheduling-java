package com.github.huchangjun118.interviewschedulingjava.tests;

import com.github.huchangjun118.interviewschedulingjava.domain.AuditRecord;
import com.github.huchangjun118.interviewschedulingjava.domain.Interview;
import com.github.huchangjun118.interviewschedulingjava.domain.InterviewStatus;
import com.github.huchangjun118.interviewschedulingjava.domain.RecruitmentStage;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import com.github.huchangjun118.interviewschedulingjava.domain.TimeWindow;
import com.github.huchangjun118.interviewschedulingjava.exception.PermissionDeniedException;
import com.github.huchangjun118.interviewschedulingjava.tests.support.TestEnv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 角色权限与重启恢复。
 */
class PermissionRecoveryTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @TempDir
    Path tempDir;

    private TestEnv newEnv() {
        return new TestEnv(tempDir.resolve("state.json"));
    }

    private void prepare(TestEnv env, String candidateId) {
        env.stageService.register(candidateId, "候选人");
        env.availabilityService.addSlot("r1", Role.RECRUITER, "r1",
                ZonedDateTime.of(2030, 1, 1, 9, 0, 0, 0, UTC),
                ZonedDateTime.of(2030, 1, 1, 17, 0, 0, 0, UTC));
        env.availabilityService.addSlot(candidateId, Role.CANDIDATE, candidateId,
                ZonedDateTime.of(2030, 1, 1, 9, 0, 0, 0, UTC),
                ZonedDateTime.of(2030, 1, 1, 17, 0, 0, 0, UTC));
    }

    @Test
    void candidateCannotScheduleInterview() {
        try (TestEnv env = newEnv()) {
            prepare(env, "c1");
            assertThrows(PermissionDeniedException.class,
                    () -> env.schedulingService.schedule("k1", "c1", Role.CANDIDATE, "c1", "r1",
                            RecruitmentStage.APPLIED,
                            ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, UTC),
                            ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, UTC)));
            // 越权已记录
            assertTrue(env.auditService.list().stream().anyMatch(a ->
                    !a.isAllowed() && "c1".equals(a.getOperatorId())
                            && a.getOperatorRole() == Role.CANDIDATE
                            && "SCHEDULE".equals(a.getAction())));
        }
    }

    @Test
    void recruiterCannotOperateOnOthersInterview() {
        try (TestEnv env = newEnv()) {
            prepare(env, "c1");
            // 招聘官 r2 不能冒用 r1 的身份安排面试
            assertThrows(PermissionDeniedException.class,
                    () -> env.schedulingService.schedule("k1", "r2", Role.RECRUITER, "c1", "r1",
                            RecruitmentStage.APPLIED,
                            ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, UTC),
                            ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, UTC)));

            Interview interview = env.schedulingService.schedule("k2", "r1", Role.RECRUITER, "c1", "r1",
                    RecruitmentStage.APPLIED,
                    ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, UTC),
                    ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, UTC));
            // 招聘官 r2 不能改期/取消 r1 的面试
            assertThrows(PermissionDeniedException.class,
                    () -> env.schedulingService.reschedule("r2", Role.RECRUITER, interview.getId(),
                            ZonedDateTime.of(2030, 1, 1, 14, 0, 0, 0, UTC),
                            ZonedDateTime.of(2030, 1, 1, 15, 0, 0, 0, UTC)));
            assertThrows(PermissionDeniedException.class,
                    () -> env.schedulingService.cancel("r2", Role.RECRUITER, interview.getId(), "越权"));
            // 候选人也不能取消
            assertThrows(PermissionDeniedException.class,
                    () -> env.schedulingService.cancel("c1", Role.CANDIDATE, interview.getId(), "越权"));
            // 面试未被改动
            assertEquals(InterviewStatus.SCHEDULED, env.schedulingService.get(interview.getId()).getStatus());
            // 管理员可以取消
            env.schedulingService.cancel("admin1", Role.ADMIN, interview.getId(), "管理员介入");
            assertEquals(InterviewStatus.CANCELLED, env.schedulingService.get(interview.getId()).getStatus());
        }
    }

    @Test
    void deniedOperationsAreAuditedWithOperatorAndReason() {
        try (TestEnv env = newEnv()) {
            prepare(env, "c1");
            assertThrows(PermissionDeniedException.class,
                    () -> env.schedulingService.schedule("k1", "c1", Role.CANDIDATE, "c1", "r1",
                            RecruitmentStage.APPLIED,
                            ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, UTC),
                            ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, UTC)));
            List<AuditRecord> denied = env.auditService.list().stream()
                    .filter(a -> !a.isAllowed()).toList();
            assertEquals(1, denied.size());
            AuditRecord record = denied.get(0);
            assertEquals("c1", record.getOperatorId());
            assertEquals(Role.CANDIDATE, record.getOperatorRole());
            assertTrue(record.getReason().contains("无权"));
            assertTrue(record.getReason().contains("CANDIDATE"));
        }
    }

    @Test
    void stateSurvivesRestart() {
        Path stateFile = tempDir.resolve("state.json");
        String interviewId;
        TimeWindow window;
        try (TestEnv env = new TestEnv(stateFile)) {
            prepare(env, "c1");
            Interview interview = env.schedulingService.schedule("k1", "r1", Role.RECRUITER, "c1", "r1",
                    RecruitmentStage.APPLIED,
                    ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, UTC),
                    ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, UTC));
            interviewId = interview.getId();
            window = interview.getWindow();
            // 推进一次阶段，验证阶段也能恢复
            env.stageService.advance("r1", Role.RECRUITER, "c1", RecruitmentStage.SCREENING);
        }

        // 模拟重启：全新服务实例从同一快照恢复
        try (TestEnv env = new TestEnv(stateFile)) {
            Interview restored = env.schedulingService.get(interviewId);
            assertEquals(InterviewStatus.SCHEDULED, restored.getStatus());
            assertEquals(window, restored.getWindow());
            // 已占用时段与面试一致：同一时段不能再安排
            assertTrue(env.availabilityService.isBooked("r1", window, null));
            assertTrue(env.availabilityService.isBooked("c1", window, null));
            // 幂等键也恢复：重复提交仍返回同一场面试
            Interview again = env.schedulingService.schedule("k1", "r1", Role.RECRUITER, "c1", "r1",
                    RecruitmentStage.SCREENING,
                    ZonedDateTime.of(2030, 1, 1, 12, 0, 0, 0, UTC),
                    ZonedDateTime.of(2030, 1, 1, 13, 0, 0, 0, UTC));
            assertEquals(interviewId, again.getId());
            assertEquals(1, env.schedulingService.list().size());
            // 候选人阶段恢复
            assertEquals(RecruitmentStage.SCREENING, env.stageService.get("c1").getStage());
            // 审计记录恢复
            assertTrue(env.auditService.list().stream()
                    .anyMatch(a -> "SCHEDULE".equals(a.getAction()) && a.isAllowed()));
            // 可用时段恢复
            assertTrue(env.availabilityService.isAvailable("r1", window));
        }
    }

    @Test
    void occupiedSlotAlwaysTraceableToInterview() {
        try (TestEnv env = newEnv()) {
            prepare(env, "c1");
            Interview interview = env.schedulingService.schedule("k1", "r1", Role.RECRUITER, "c1", "r1",
                    RecruitmentStage.APPLIED,
                    ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, UTC),
                    ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, UTC));
            // 不变式：任何被占用的时段都能查到对应的 SCHEDULED 面试
            TimeWindow w = new TimeWindow(Instant.parse("2030-01-01T10:00:00Z"),
                    Instant.parse("2030-01-01T11:00:00Z"));
            boolean booked = env.availabilityService.isBooked("r1", w, null);
            boolean traceable = env.schedulingService.list().stream()
                    .anyMatch(i -> i.getStatus() == InterviewStatus.SCHEDULED
                            && i.getInterviewerId().equals("r1")
                            && i.getWindow().overlaps(w));
            assertEquals(booked, traceable);
            assertEquals(interview.getId(), env.schedulingService.list().get(0).getId());
        }
    }
}
