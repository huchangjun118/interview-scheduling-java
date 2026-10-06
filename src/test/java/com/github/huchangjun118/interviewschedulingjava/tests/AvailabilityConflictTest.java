package com.github.huchangjun118.interviewschedulingjava.tests;

import com.github.huchangjun118.interviewschedulingjava.domain.AvailabilitySlot;
import com.github.huchangjun118.interviewschedulingjava.domain.RecruitmentStage;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import com.github.huchangjun118.interviewschedulingjava.domain.TimeWindow;
import com.github.huchangjun118.interviewschedulingjava.exception.PermissionDeniedException;
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
 * 可用时段维护与冲突检测：时区换算、首尾相接、重叠冲突。
 */
class AvailabilityConflictTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @TempDir
    Path tempDir;

    private TestEnv newEnv() {
        return new TestEnv(tempDir.resolve("state.json"));
    }

    @Test
    void slotsInDifferentTimezonesNormalizeToUtc() {
        try (TestEnv env = newEnv()) {
            // 上海 18:00-19:00 == UTC 10:00-11:00
            AvailabilitySlot slot = env.availabilityService.addSlot("r1", Role.RECRUITER, "r1",
                    ZonedDateTime.of(2030, 1, 1, 18, 0, 0, 0, SHANGHAI),
                    ZonedDateTime.of(2030, 1, 1, 19, 0, 0, 0, SHANGHAI));
            assertEquals(Instant.parse("2030-01-01T10:00:00Z"), slot.getWindow().getStart());
            assertEquals(Instant.parse("2030-01-01T11:00:00Z"), slot.getWindow().getEnd());

            // 纽约 05:00-06:00 (UTC-5) == UTC 10:00-11:00，与上面是同一时段
            TimeWindow nyWindow = env.availabilityService.toUtcWindow(
                    ZonedDateTime.of(2030, 1, 1, 5, 0, 0, 0, NEW_YORK),
                    ZonedDateTime.of(2030, 1, 1, 6, 0, 0, 0, NEW_YORK));
            assertEquals(slot.getWindow(), nyWindow);
        }
    }

    @Test
    void adjacentWindowsDoNotConflict() {
        TimeWindow a = new TimeWindow(Instant.parse("2030-01-01T10:00:00Z"),
                Instant.parse("2030-01-01T11:00:00Z"));
        TimeWindow b = new TimeWindow(Instant.parse("2030-01-01T11:00:00Z"),
                Instant.parse("2030-01-01T12:00:00Z"));
        assertFalse(a.overlaps(b));
        assertFalse(b.overlaps(a));
        assertTrue(a.overlaps(a));
    }

    @Test
    void backToBackInterviewsAllowedForSameInterviewer() {
        try (TestEnv env = newEnv()) {
            env.stageService.register("c1", "候选人1");
            env.stageService.register("c2", "候选人2");
            // 面试官可用 10:00-12:00 UTC，两位候选人全程可用
            env.availabilityService.addSlot("r1", Role.RECRUITER, "r1",
                    ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneId.of("UTC")),
                    ZonedDateTime.of(2030, 1, 1, 12, 0, 0, 0, ZoneId.of("UTC")));
            env.availabilityService.addSlot("c1", Role.CANDIDATE, "c1",
                    ZonedDateTime.of(2030, 1, 1, 9, 0, 0, 0, ZoneId.of("UTC")),
                    ZonedDateTime.of(2030, 1, 1, 13, 0, 0, 0, ZoneId.of("UTC")));
            env.availabilityService.addSlot("c2", Role.CANDIDATE, "c2",
                    ZonedDateTime.of(2030, 1, 1, 9, 0, 0, 0, ZoneId.of("UTC")),
                    ZonedDateTime.of(2030, 1, 1, 13, 0, 0, 0, ZoneId.of("UTC")));

            // 10:00-11:00 与 11:00-12:00 首尾相接，都应成功
            env.schedulingService.schedule("k1", "r1", Role.RECRUITER, "c1", "r1",
                    RecruitmentStage.APPLIED,
                    ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneId.of("UTC")),
                    ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, ZoneId.of("UTC")));
            env.schedulingService.schedule("k2", "r1", Role.RECRUITER, "c2", "r1",
                    RecruitmentStage.APPLIED,
                    ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, ZoneId.of("UTC")),
                    ZonedDateTime.of(2030, 1, 1, 12, 0, 0, 0, ZoneId.of("UTC")));
            assertEquals(2, env.schedulingService.list().size());
        }
    }

    @Test
    void overlappingInterviewRejectedWithAlternatives() {
        try (TestEnv env = newEnv()) {
            env.stageService.register("c1", "候选人1");
            env.stageService.register("c2", "候选人2");
            env.availabilityService.addSlot("r1", Role.RECRUITER, "r1",
                    ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneId.of("UTC")),
                    ZonedDateTime.of(2030, 1, 1, 12, 0, 0, 0, ZoneId.of("UTC")));
            for (String c : new String[]{"c1", "c2"}) {
                env.availabilityService.addSlot(c, Role.CANDIDATE, c,
                        ZonedDateTime.of(2030, 1, 1, 9, 0, 0, 0, ZoneId.of("UTC")),
                        ZonedDateTime.of(2030, 1, 1, 13, 0, 0, 0, ZoneId.of("UTC")));
            }
            env.schedulingService.schedule("k1", "r1", Role.RECRUITER, "c1", "r1",
                    RecruitmentStage.APPLIED,
                    ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneId.of("UTC")),
                    ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, ZoneId.of("UTC")));

            // 10:30-11:30 与已有面试重叠，且冲突结果要携带替代时段
            SchedulingConflictException e = assertThrows(SchedulingConflictException.class,
                    () -> env.schedulingService.schedule("k2", "r1", Role.RECRUITER, "c2", "r1",
                            RecruitmentStage.APPLIED,
                            ZonedDateTime.of(2030, 1, 1, 10, 30, 0, 0, ZoneId.of("UTC")),
                            ZonedDateTime.of(2030, 1, 1, 11, 30, 0, 0, ZoneId.of("UTC"))));
            assertTrue(e.getMessage().contains("r1"));
            assertFalse(e.getAlternatives().isEmpty(), "冲突时应提供替代时段");
            // 替代时段不得与已占用时段冲突
            for (TimeWindow alt : e.getAlternatives()) {
                assertFalse(env.availabilityService.isBooked("r1", alt, null));
            }
        }
    }

    @Test
    void crossTimezoneConflictDetected() {
        try (TestEnv env = newEnv()) {
            env.stageService.register("c1", "候选人1");
            env.stageService.register("c2", "候选人2");
            // 面试官可用：上海 18:00-20:00 == UTC 10:00-12:00
            env.availabilityService.addSlot("r1", Role.RECRUITER, "r1",
                    ZonedDateTime.of(2030, 1, 1, 18, 0, 0, 0, SHANGHAI),
                    ZonedDateTime.of(2030, 1, 1, 20, 0, 0, 0, SHANGHAI));
            for (String c : new String[]{"c1", "c2"}) {
                env.availabilityService.addSlot(c, Role.CANDIDATE, c,
                        ZonedDateTime.of(2030, 1, 1, 9, 0, 0, 0, ZoneId.of("UTC")),
                        ZonedDateTime.of(2030, 1, 1, 13, 0, 0, 0, ZoneId.of("UTC")));
            }
            // 第一场：UTC 10:00-11:00
            env.schedulingService.schedule("k1", "r1", Role.RECRUITER, "c1", "r1",
                    RecruitmentStage.APPLIED,
                    ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneId.of("UTC")),
                    ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, ZoneId.of("UTC")));
            // 第二场用纽约时区表达同一物理时段：纽约 05:30-06:30 == UTC 10:30-11:30，必须判冲突
            assertThrows(SchedulingConflictException.class,
                    () -> env.schedulingService.schedule("k2", "r1", Role.RECRUITER, "c2", "r1",
                            RecruitmentStage.APPLIED,
                            ZonedDateTime.of(2030, 1, 1, 5, 30, 0, 0, NEW_YORK),
                            ZonedDateTime.of(2030, 1, 1, 6, 30, 0, 0, NEW_YORK)));
        }
    }

    @Test
    void candidateCannotMaintainOthersAvailability() {
        try (TestEnv env = newEnv()) {
            assertThrows(PermissionDeniedException.class,
                    () -> env.availabilityService.addSlot("c1", Role.CANDIDATE, "r1",
                            ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneId.of("UTC")),
                            ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, ZoneId.of("UTC"))));
        }
    }
}
