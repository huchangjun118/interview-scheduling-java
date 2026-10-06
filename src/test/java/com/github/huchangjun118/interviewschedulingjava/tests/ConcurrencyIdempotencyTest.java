package com.github.huchangjun118.interviewschedulingjava.tests;

import com.github.huchangjun118.interviewschedulingjava.domain.Interview;
import com.github.huchangjun118.interviewschedulingjava.domain.InterviewStatus;
import com.github.huchangjun118.interviewschedulingjava.domain.RecruitmentStage;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import com.github.huchangjun118.interviewschedulingjava.exception.SchedulingConflictException;
import com.github.huchangjun118.interviewschedulingjava.tests.support.TestEnv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发抢占同一时段只允许一个成功；重复提交同一请求不产生重复面试。
 */
class ConcurrencyIdempotencyTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @TempDir
    Path tempDir;

    private TestEnv newEnv() {
        return new TestEnv(tempDir.resolve("state.json"));
    }

    @Test
    void duplicateRequestKeyReturnsSameInterview() {
        try (TestEnv env = newEnv()) {
            prepare(env, "c1");
            Interview first = env.schedulingService.schedule("req-1", "r1", Role.RECRUITER, "c1", "r1",
                    RecruitmentStage.APPLIED,
                    ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, UTC),
                    ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, UTC));
            // 重复提交同一安排请求
            Interview second = env.schedulingService.schedule("req-1", "r1", Role.RECRUITER, "c1", "r1",
                    RecruitmentStage.APPLIED,
                    ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, UTC),
                    ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, UTC));
            assertEquals(first.getId(), second.getId());
            assertEquals(1, env.schedulingService.list().size());
        }
    }

    @Test
    void concurrentRequestsWithSameKeyCreateOnlyOneInterview() throws Exception {
        try (TestEnv env = newEnv()) {
            prepare(env, "c1");
            int threads = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Interview>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return env.schedulingService.schedule("req-dup", "r1", Role.RECRUITER, "c1", "r1",
                            RecruitmentStage.APPLIED,
                            ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, UTC),
                            ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, UTC));
                }));
            }
            ready.await();
            go.countDown();
            List<String> ids = new ArrayList<>();
            for (Future<Interview> f : futures) {
                ids.add(f.get(10, TimeUnit.SECONDS).getId());
            }
            pool.shutdown();
            assertEquals(1, env.schedulingService.list().size());
            assertEquals(1, ids.stream().distinct().count(), "并发重复提交只能产生一场面试");
        }
    }

    @Test
    void concurrentCompetitionForSameSlotAllowsOnlyOneWinner() throws Exception {
        try (TestEnv env = newEnv()) {
            int contenders = 6;
            // 所有人都抢 10:00-11:00，但面试官 10:00-13:00 可用，失败方应能拿到替代时段
            env.availabilityService.addSlot("r1", Role.RECRUITER, "r1",
                    ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, UTC),
                    ZonedDateTime.of(2030, 1, 1, 13, 0, 0, 0, UTC));
            for (int i = 0; i < contenders; i++) {
                String cid = "c" + i;
                env.stageService.register(cid, "候选人" + i);
                env.availabilityService.addSlot(cid, Role.CANDIDATE, cid,
                        ZonedDateTime.of(2030, 1, 1, 9, 0, 0, 0, UTC),
                        ZonedDateTime.of(2030, 1, 1, 12, 0, 0, 0, UTC));
            }
            ExecutorService pool = Executors.newFixedThreadPool(contenders);
            CountDownLatch ready = new CountDownLatch(contenders);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < contenders; i++) {
                String cid = "c" + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        env.schedulingService.schedule("req-" + cid, "r1", Role.RECRUITER, cid, "r1",
                                RecruitmentStage.APPLIED,
                                ZonedDateTime.of(2030, 1, 1, 10, 0, 0, 0, UTC),
                                ZonedDateTime.of(2030, 1, 1, 11, 0, 0, 0, UTC));
                        return "OK";
                    } catch (SchedulingConflictException e) {
                        assertFalse(e.getAlternatives().isEmpty(), "失败方应拿到替代时段");
                        return "CONFLICT";
                    }
                }));
            }
            ready.await();
            go.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> f : futures) {
                results.add(f.get(15, TimeUnit.SECONDS));
            }
            pool.shutdown();
            assertEquals(1, results.stream().filter("OK"::equals).count(),
                    "同一时段只能有一个成功者");
            assertEquals(contenders - 1, results.stream().filter("CONFLICT"::equals).count());
            // 占用与面试记录一致：恰好一场 SCHEDULED 面试
            assertEquals(1, env.schedulingService.list().stream()
                    .filter(iv -> iv.getStatus() == InterviewStatus.SCHEDULED).count());
        }
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
}
