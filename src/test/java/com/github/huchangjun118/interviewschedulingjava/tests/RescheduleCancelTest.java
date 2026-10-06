package com.github.huchangjun118.interviewschedulingjava.tests;

import com.github.huchangjun118.interviewschedulingjava.domain.Interview;
import com.github.huchangjun118.interviewschedulingjava.domain.InterviewStatus;
import com.github.huchangjun118.interviewschedulingjava.domain.RecruitmentStage;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import com.github.huchangjun118.interviewschedulingjava.domain.TimeWindow;
import com.github.huchangjun118.interviewschedulingjava.exception.InvalidOperationException;
import com.github.huchangjun118.interviewschedulingjava.exception.SchedulingConflictException;
import com.github.huchangjun118.interviewschedulingjava.tests.support.TestEnv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 改期原子性、取消最小提前量、已开始/已结束保护。
 */
class RescheduleCancelTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    // 时钟起点 2030-01-01T00:00Z，面试安排在当天 10 点以后，均在未来
    private static final Instant T0 = TestEnv.T0;

    @TempDir
    Path tempDir;

    private TestEnv newEnv() {
        return new TestEnv(tempDir.resolve("state.json"));
    }

    private Interview scheduleAt(TestEnv env, String key, String candidateId, int startHour) {
        return env.schedulingService.schedule(key, "r1", Role.RECRUITER, candidateId, "r1",
                RecruitmentStage.APPLIED,
                ZonedDateTime.of(2030, 1, 1, startHour, 0, 0, 0, UTC),
                ZonedDateTime.of(2030, 1, 1, startHour + 1, 0, 0, 0, UTC));
    }

    private void prepare(TestEnv env, String... candidateIds) {
        env.availabilityService.addSlot("r1", Role.RECRUITER, "r1",
                ZonedDateTime.of(2030, 1, 1, 8, 0, 0, 0, UTC),
                ZonedDateTime.of(2030, 1, 1, 20, 0, 0, 0, UTC));
        for (String cid : candidateIds) {
            env.stageService.register(cid, "候选人" + cid);
            env.availabilityService.addSlot(cid, Role.CANDIDATE, cid,
                    ZonedDateTime.of(2030, 1, 1, 8, 0, 0, 0, UTC),
                    ZonedDateTime.of(2030, 1, 1, 20, 0, 0, 0, UTC));
        }
    }

    @Test
    void rescheduleReleasesOldSlotAndOccupiesNewOne() {
        try (TestEnv env = newEnv()) {
            prepare(env, "c1", "c2");
            Interview interview = scheduleAt(env, "k1", "c1", 10);
            TimeWindow oldWindow = interview.getWindow();

            // 改期到 14:00-15:00
            Interview moved = env.schedulingService.reschedule("r1", Role.RECRUITER, interview.getId(),
                    ZonedDateTime.of(2030, 1, 1, 14, 0, 0, 0, UTC),
                    ZonedDateTime.of(2030, 1, 1, 15, 0, 0, 0, UTC));
            assertEquals(Instant.parse("2030-01-01T14:00:00Z"), moved.getWindow().getStart());

            // 旧时段已释放、新时段已占用
            assertFalse(env.availabilityService.isBooked("r1", oldWindow, null));
            assertTrue(env.availabilityService.isBooked("r1", moved.getWindow(), null));

            // 旧时段可再被占用：候选人 c2 可以安排到 10:00-11:00
            env.schedulingService.schedule("k2", "r1", Role.RECRUITER, "c2", "r1",
                    RecruitmentStage.APPLIED,
                    ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, UTC),
                    ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, UTC));
        }
    }

    @Test
    void failedRescheduleKeepsOldSlotOccupied() {
        try (TestEnv env = newEnv()) {
            prepare(env, "c1", "c2");
            Interview first = scheduleAt(env, "k1", "c1", 10);
            scheduleAt(env, "k2", "c2", 12);

            // 试图改期到与 c2 冲突的 12:00-13:00，应失败
            assertThrows(SchedulingConflictException.class,
                    () -> env.schedulingService.reschedule("r1", Role.RECRUITER, first.getId(),
                            ZonedDateTime.of(2030, 1, 1, 12, 0, 0, 0, UTC),
                            ZonedDateTime.of(2030, 1, 1, 13, 0, 0, 0, UTC)));

            // 原子性：旧时段仍被占用，不存在"已释放却未占用"的空档
            Interview after = env.schedulingService.get(first.getId());
            assertEquals(Instant.parse("2030-01-01T10:00:00Z"), after.getWindow().getStart());
            assertTrue(env.availabilityService.isBooked("r1",
                    new TimeWindow(Instant.parse("2030-01-01T10:00:00Z"),
                            Instant.parse("2030-01-01T11:00:00Z")), null));
        }
    }

    @Test
    void cancelWithinMinLeadTimeRejected() {
        try (TestEnv env = newEnv()) {
            prepare(env, "c1");
            Interview interview = scheduleAt(env, "k1", "c1", 10);
            // 推进时钟到 09:00，距开始仅 1 小时，小于最小提前量 2 小时
            env.clock.setInstant(Instant.parse("2030-01-01T09:00:00Z"));
            InvalidOperationException e = assertThrows(InvalidOperationException.class,
                    () -> env.schedulingService.cancel("r1", Role.RECRUITER, interview.getId(), "计划变更"));
            assertTrue(e.getMessage().contains("提前量"));
            assertEquals(InterviewStatus.SCHEDULED, env.schedulingService.get(interview.getId()).getStatus());
        }
    }

    @Test
    void cancelBeforeMinLeadTimeSucceedsAndReleasesSlot() {
        try (TestEnv env = newEnv()) {
            prepare(env, "c1", "c2");
            Interview interview = scheduleAt(env, "k1", "c1", 10);
            // 时钟在 07:00，距开始 3 小时 > 2 小时
            env.clock.setInstant(Instant.parse("2030-01-01T07:00:00Z"));
            Interview cancelled = env.schedulingService.cancel("r1", Role.RECRUITER, interview.getId(), "计划变更");
            assertEquals(InterviewStatus.CANCELLED, cancelled.getStatus());
            // 时段已释放，他人可占用
            env.schedulingService.schedule("k2", "r1", Role.RECRUITER, "c2", "r1",
                    RecruitmentStage.APPLIED,
                    ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, UTC),
                    ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, UTC));
        }
    }

    @Test
    void startedInterviewCannotBeRescheduledOrCancelled() {
        try (TestEnv env = newEnv()) {
            prepare(env, "c1");
            Interview interview = scheduleAt(env, "k1", "c1", 10);
            // 推进时钟到 10:30，面试已开始
            env.clock.setInstant(Instant.parse("2030-01-01T10:30:00Z"));
            assertThrows(InvalidOperationException.class,
                    () -> env.schedulingService.cancel("r1", Role.RECRUITER, interview.getId(), "迟到"));
            assertThrows(InvalidOperationException.class,
                    () -> env.schedulingService.reschedule("r1", Role.RECRUITER, interview.getId(),
                            ZonedDateTime.of(2030, 1, 1, 14, 0, 0, 0, UTC),
                            ZonedDateTime.of(2030, 1, 1, 15, 0, 0, 0, UTC)));
            // 已结束的面试同样不允许（推进到次日）
            env.clock.setInstant(Instant.parse("2030-01-02T00:00:00Z"));
            assertThrows(InvalidOperationException.class,
                    () -> env.schedulingService.cancel("r1", Role.RECRUITER, interview.getId(), "事后"));
        }
    }

    @Test
    void cancelledInterviewCannotBeRescheduledOrCancelledAgain() {
        try (TestEnv env = newEnv()) {
            prepare(env, "c1");
            Interview interview = scheduleAt(env, "k1", "c1", 10);
            env.schedulingService.cancel("r1", Role.RECRUITER, interview.getId(), "计划变更");
            assertThrows(InvalidOperationException.class,
                    () -> env.schedulingService.cancel("r1", Role.RECRUITER, interview.getId(), "重复取消"));
            assertThrows(InvalidOperationException.class,
                    () -> env.schedulingService.reschedule("r1", Role.RECRUITER, interview.getId(),
                            ZonedDateTime.of(2030, 1, 1, 14, 0, 0, 0, UTC),
                            ZonedDateTime.of(2030, 1, 1, 15, 0, 0, 0, UTC)));
        }
    }
}
