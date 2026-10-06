package com.github.huchangjun118.interviewschedulingjava.tests;

import com.github.huchangjun118.interviewschedulingjava.ScheduleProperties;
import com.github.huchangjun118.interviewschedulingjava.domain.InterviewStatus;
import com.github.huchangjun118.interviewschedulingjava.dto.ScheduleRequest;
import com.github.huchangjun118.interviewschedulingjava.dto.ScheduleResult;
import com.github.huchangjun118.interviewschedulingjava.dto.TimeWindow;
import com.github.huchangjun118.interviewschedulingjava.service.AuditService;
import com.github.huchangjun118.interviewschedulingjava.service.AvailabilityService;
import com.github.huchangjun118.interviewschedulingjava.service.CandidateService;
import com.github.huchangjun118.interviewschedulingjava.service.InterviewService;
import com.github.huchangjun118.interviewschedulingjava.service.PermissionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发排期测试：多个请求同时抢占同一时段时只允许一个成功。
 */
class ConcurrencyTest {

    @Test
    void concurrentBookingSameSlotOnlyOneWins() throws Exception {
        AuditService auditService = new AuditService();
        CandidateService candidateService = new CandidateService(auditService);
        AvailabilityService availabilityService = new AvailabilityService();
        InterviewService interviewService = new InterviewService(candidateService,
                availabilityService, auditService, new PermissionService(auditService),
                new ScheduleProperties());

        Instant base = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant end = base.plus(1, ChronoUnit.HOURS);
        interviewService.registerInterviewer("int1", "面试官");
        availabilityService.addSlot("int1", base.minus(1, ChronoUnit.HOURS), end.plus(1, ChronoUnit.HOURS));

        int threads = 16;
        for (int i = 0; i < threads; i++) {
            candidateService.register("cand" + i, "候选人" + i);
            availabilityService.addSlot("cand" + i, base.minus(1, ChronoUnit.HOURS), end.plus(1, ChronoUnit.HOURS));
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ScheduleResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final String candidateId = "cand" + i;
            final String requestId = "req-" + i;
            futures.add(pool.submit(() -> {
                ScheduleRequest req = new ScheduleRequest();
                req.setRequestId(requestId);
                req.setCandidateId(candidateId);
                req.setInterviewerId("int1");
                req.setWindow(new TimeWindow(base.toString(), end.toString()));
                req.setOperatorId("rec1");
                req.setOperatorRole("RECRUITER");
                ready.countDown();
                go.await();
                return interviewService.schedule(req);
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();

        int successes = 0;
        int conflicts = 0;
        for (Future<ScheduleResult> f : futures) {
            ScheduleResult r = f.get(10, TimeUnit.SECONDS);
            if (r.isSuccess()) {
                successes++;
            } else if (r.isConflict()) {
                conflicts++;
                assertTrue(r.getMessage() != null && !r.getMessage().isBlank(),
                        "失败方要拿到明确的冲突结果");
            }
        }
        pool.shutdown();

        assertEquals(1, successes, "同一时段只能有一个请求成功");
        assertEquals(threads - 1, conflicts, "其余请求必须拿到冲突结果");
        long activeCount = interviewService.listAll().stream()
                .filter(iv -> iv.getStatus() == InterviewStatus.SCHEDULED)
                .filter(iv -> iv.getInterviewerId().equals("int1"))
                .filter(iv -> iv.getStart().equals(base))
                .count();
        assertEquals(1, activeCount, "不得出现同一时段被两人同时占用");
    }

    @Test
    void concurrentDuplicateRequestIdStaysIdempotent() throws Exception {
        AuditService auditService = new AuditService();
        CandidateService candidateService = new CandidateService(auditService);
        AvailabilityService availabilityService = new AvailabilityService();
        InterviewService interviewService = new InterviewService(candidateService,
                availabilityService, auditService, new PermissionService(auditService),
                new ScheduleProperties());

        Instant base = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant end = base.plus(1, ChronoUnit.HOURS);
        candidateService.register("cand1", "候选人");
        interviewService.registerInterviewer("int1", "面试官");
        availabilityService.addSlot("cand1", base, end);
        availabilityService.addSlot("int1", base, end);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ScheduleResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ScheduleRequest req = new ScheduleRequest();
                req.setRequestId("same-request-id");
                req.setCandidateId("cand1");
                req.setInterviewerId("int1");
                req.setWindow(new TimeWindow(base.toString(), end.toString()));
                req.setOperatorId("rec1");
                req.setOperatorRole("ADMIN");
                go.await();
                return interviewService.schedule(req);
            }));
        }
        go.countDown();
        String firstId = null;
        for (Future<ScheduleResult> f : futures) {
            ScheduleResult r = f.get(10, TimeUnit.SECONDS);
            assertTrue(r.isSuccess(), "同一 requestId 的重复提交都应返回成功结果");
            if (firstId == null) {
                firstId = r.getInterview().getId();
            } else {
                assertEquals(firstId, r.getInterview().getId());
            }
        }
        pool.shutdown();
        assertEquals(1, interviewService.listAll().size(), "并发重复提交不得产生重复面试");
    }
}
