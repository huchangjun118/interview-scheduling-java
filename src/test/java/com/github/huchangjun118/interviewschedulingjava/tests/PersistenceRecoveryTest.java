package com.github.huchangjun118.interviewschedulingjava.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.huchangjun118.interviewschedulingjava.ScheduleProperties;
import com.github.huchangjun118.interviewschedulingjava.domain.InterviewStatus;
import com.github.huchangjun118.interviewschedulingjava.domain.Stage;
import com.github.huchangjun118.interviewschedulingjava.dto.ScheduleRequest;
import com.github.huchangjun118.interviewschedulingjava.dto.ScheduleResult;
import com.github.huchangjun118.interviewschedulingjava.dto.TimeWindow;
import com.github.huchangjun118.interviewschedulingjava.service.AuditService;
import com.github.huchangjun118.interviewschedulingjava.service.AvailabilityService;
import com.github.huchangjun118.interviewschedulingjava.service.CandidateService;
import com.github.huchangjun118.interviewschedulingjava.service.InterviewService;
import com.github.huchangjun118.interviewschedulingjava.service.PermissionService;
import com.github.huchangjun118.interviewschedulingjava.service.PersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 持久化与恢复测试：重启后已确认面试与已占用时段保持一致。
 */
class PersistenceRecoveryTest {

    private static final Instant BASE = Instant.now().plus(3, ChronoUnit.DAYS)
            .truncatedTo(ChronoUnit.HOURS);

    private static class Services {
        AuditService audit = new AuditService();
        CandidateService candidates = new CandidateService(audit);
        AvailabilityService availability = new AvailabilityService();
        PermissionService permissions = new PermissionService(audit);
        ScheduleProperties properties = new ScheduleProperties();
        InterviewService interviews = new InterviewService(candidates, availability,
                audit, permissions, properties);
        PersistenceService persistence;

        Services(Path file) {
            properties.setPersistenceFile(file.toString());
            properties.setPersistenceEnabled(true);
            ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            persistence = new PersistenceService(properties, candidates, interviews,
                    availability, audit, mapper);
        }
    }

    private ScheduleRequest request(String requestId, String candidateId, Instant start, Instant end) {
        ScheduleRequest req = new ScheduleRequest();
        req.setRequestId(requestId);
        req.setCandidateId(candidateId);
        req.setInterviewerId("int1");
        req.setWindow(new TimeWindow(start.toString(), end.toString()));
        req.setOperatorId("rec1");
        req.setOperatorRole("RECRUITER");
        return req;
    }

    @Test
    void stateSurvivesRestart(@TempDir Path dir) {
        Path file = dir.resolve("state.json");

        // 第一次"运行"：注册、推进阶段、安排并改期一场面试、取消一场面试
        Services first = new Services(file);
        first.candidates.register("cand1", "候选人一");
        first.candidates.register("cand2", "候选人二");
        first.candidates.advanceStage("cand1", Stage.SCREENING, "rec1",
                com.github.huchangjun118.interviewschedulingjava.domain.Role.RECRUITER);
        first.interviews.registerInterviewer("int1", "面试官一");
        first.availability.addSlot("int1", BASE, BASE.plus(8, ChronoUnit.HOURS));
        first.availability.addSlot("cand1", BASE, BASE.plus(8, ChronoUnit.HOURS));
        first.availability.addSlot("cand2", BASE, BASE.plus(8, ChronoUnit.HOURS));

        ScheduleResult scheduled = first.interviews.schedule(
                request("req-1", "cand1", BASE, BASE.plus(1, ChronoUnit.HOURS)));
        assertTrue(scheduled.isSuccess());
        String interviewId = scheduled.getInterview().getId();
        first.interviews.reschedule(interviewId, BASE.plus(2, ChronoUnit.HOURS),
                BASE.plus(3, ChronoUnit.HOURS), "rec1",
                com.github.huchangjun118.interviewschedulingjava.domain.Role.RECRUITER, "调整");
        ScheduleResult toCancel = first.interviews.schedule(
                request("req-2", "cand2", BASE.plus(4, ChronoUnit.HOURS), BASE.plus(5, ChronoUnit.HOURS)));
        first.interviews.cancel(toCancel.getInterview().getId(), "rec1",
                com.github.huchangjun118.interviewschedulingjava.domain.Role.RECRUITER, "取消");
        first.persistence.saveOnShutdown();

        // 第二次"运行"：全新服务实例从快照恢复
        Services second = new Services(file);
        second.persistence.loadOnStartup();

        // 候选人阶段恢复
        assertEquals(Stage.SCREENING, second.candidates.get("cand1").getStage());
        // 已确认面试恢复，状态与时间一致
        var restored = second.interviews.get(interviewId);
        assertEquals(InterviewStatus.RESCHEDULED, restored.getStatus());
        assertEquals(BASE.plus(2, ChronoUnit.HOURS), restored.getStart());
        // 已占用时段仍被占用：同时段再排必须冲突
        ScheduleResult clash = second.interviews.schedule(
                request("req-3", "cand2", BASE.plus(2, ChronoUnit.HOURS), BASE.plus(3, ChronoUnit.HOURS)));
        assertTrue(clash.isConflict(), "重启后已占用时段必须仍被占用");
        // 已取消面试的时段已释放
        ScheduleResult reuse = second.interviews.schedule(
                request("req-4", "cand2", BASE.plus(4, ChronoUnit.HOURS), BASE.plus(5, ChronoUnit.HOURS)));
        assertTrue(reuse.isSuccess(), "重启后已取消时段必须可用");
        // 幂等索引恢复：重复提交原 requestId 返回原面试
        ScheduleResult dup = second.interviews.schedule(
                request("req-1", "cand1", BASE, BASE.plus(1, ChronoUnit.HOURS)));
        assertTrue(dup.isSuccess());
        assertEquals(interviewId, dup.getInterview().getId());
        // 审计记录恢复
        assertTrue(second.audit.listByTarget(interviewId).size() >= 2,
                "安排与改期的审计记录应恢复");
    }
}
