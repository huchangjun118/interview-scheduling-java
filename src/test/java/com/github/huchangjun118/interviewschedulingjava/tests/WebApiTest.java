package com.github.huchangjun118.interviewschedulingjava.tests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web 层集成测试：排期、冲突、越权、阶段流转与审计查询。
 */
@SpringBootTest(properties = "scheduling.persistence-enabled=false")
@AutoConfigureMockMvc
class WebApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.github.huchangjun118.interviewschedulingjava.service.CandidateService candidateService;
    @Autowired
    private com.github.huchangjun118.interviewschedulingjava.service.InterviewService interviewService;
    @Autowired
    private com.github.huchangjun118.interviewschedulingjava.service.AvailabilityService availabilityService;
    @Autowired
    private com.github.huchangjun118.interviewschedulingjava.service.AuditService auditService;

    private static final Instant BASE = Instant.now().plus(3, ChronoUnit.DAYS)
            .truncatedTo(ChronoUnit.HOURS);

    @BeforeEach
    void reset() {
        candidateService.clear();
        interviewService.clear();
        availabilityService.clear();
        auditService.clear();
    }

    private void seed() throws Exception {
        mockMvc.perform(post("/candidates").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"c1\",\"name\":\"候选人\"}")).andExpect(status().isOk());
        mockMvc.perform(post("/interviews/interviewers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"i1\",\"name\":\"面试官\"}")).andExpect(status().isOk());
        String avail = "{\"start\":\"" + BASE.minus(1, ChronoUnit.HOURS) + "\",\"end\":\""
                + BASE.plus(9, ChronoUnit.HOURS) + "\"}";
        mockMvc.perform(post("/availability/c1").contentType(MediaType.APPLICATION_JSON)
                .content(avail)).andExpect(status().isOk());
        mockMvc.perform(post("/availability/i1").contentType(MediaType.APPLICATION_JSON)
                .content(avail)).andExpect(status().isOk());
    }

    @Test
    void scheduleConflictAndAuditFlow() throws Exception {
        seed();
        String schedule = "{\"requestId\":\"r1\",\"candidateId\":\"c1\",\"interviewerId\":\"i1\","
                + "\"window\":{\"start\":\"" + BASE + "\",\"end\":\""
                + BASE.plus(1, ChronoUnit.HOURS) + "\"},"
                + "\"operatorId\":\"rec1\",\"operatorRole\":\"RECRUITER\"}";
        mockMvc.perform(post("/interviews").contentType(MediaType.APPLICATION_JSON).content(schedule))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 同一时段冲突 -> 409 + 替代时段
        String conflictReq = schedule.replace("r1", "r2");
        mockMvc.perform(post("/interviews").contentType(MediaType.APPLICATION_JSON).content(conflictReq))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflict").value(true))
                .andExpect(jsonPath("$.alternatives.length()").value(greaterThanOrEqualTo(1)));

        // 幂等重试 -> 200 且同一场面试
        mockMvc.perform(post("/interviews").contentType(MediaType.APPLICATION_JSON).content(schedule))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 审计可查询
        mockMvc.perform(get("/audits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void permissionDeniedReturns403AndIsAudited() throws Exception {
        seed();
        String schedule = "{\"requestId\":\"r9\",\"candidateId\":\"c1\",\"interviewerId\":\"i1\","
                + "\"window\":{\"start\":\"" + BASE + "\",\"end\":\""
                + BASE.plus(1, ChronoUnit.HOURS) + "\"},"
                + "\"operatorId\":\"c1\",\"operatorRole\":\"CANDIDATE\"}";
        mockMvc.perform(post("/interviews").contentType(MediaType.APPLICATION_JSON).content(schedule))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(get("/audits").param("targetId", "c1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("PERMISSION_DENIED"))
                .andExpect(jsonPath("$[0].operatorId").value("c1"));
    }

    @Test
    void illegalStageAdvanceReturns400WithReason() throws Exception {
        seed();
        String advance = "{\"targetStage\":\"OFFER\",\"operatorId\":\"rec1\",\"operatorRole\":\"ADMIN\"}";
        mockMvc.perform(post("/candidates/c1/advance").contentType(MediaType.APPLICATION_JSON)
                .content(advance))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STAGE_TRANSITION"))
                .andExpect(jsonPath("$.message").value(containsString("APPLIED")))
                .andExpect(jsonPath("$.message").value(containsString("SCREENING")));
        // 合法推进
        String legal = "{\"targetStage\":\"SCREENING\",\"operatorId\":\"rec1\",\"operatorRole\":\"RECRUITER\"}";
        mockMvc.perform(post("/candidates/c1/advance").contentType(MediaType.APPLICATION_JSON)
                .content(legal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("SCREENING"));
        // 候选人无权推进阶段
        String denied = "{\"targetStage\":\"INTERVIEWING\",\"operatorId\":\"c1\",\"operatorRole\":\"CANDIDATE\"}";
        mockMvc.perform(post("/candidates/c1/advance").contentType(MediaType.APPLICATION_JSON)
                .content(denied))
                .andExpect(status().isForbidden());
    }

    @Test
    void rescheduleAndCancelFlow() throws Exception {
        seed();
        String schedule = "{\"requestId\":\"r5\",\"candidateId\":\"c1\",\"interviewerId\":\"i1\","
                + "\"window\":{\"start\":\"" + BASE + "\",\"end\":\""
                + BASE.plus(1, ChronoUnit.HOURS) + "\"},"
                + "\"operatorId\":\"rec1\",\"operatorRole\":\"RECRUITER\"}";
        String response = mockMvc.perform(post("/interviews").contentType(MediaType.APPLICATION_JSON)
                .content(schedule))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.interview.id");

        Instant newStart = BASE.plus(3, ChronoUnit.HOURS);
        String reschedule = "{\"newWindow\":{\"start\":\"" + newStart + "\",\"end\":\""
                + newStart.plus(1, ChronoUnit.HOURS) + "\"},"
                + "\"operatorId\":\"rec1\",\"operatorRole\":\"RECRUITER\",\"reason\":\"调整\"}";
        mockMvc.perform(post("/interviews/" + id + "/reschedule")
                .contentType(MediaType.APPLICATION_JSON).content(reschedule))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interview.status").value("RESCHEDULED"));

        String cancel = "{\"operatorId\":\"rec1\",\"operatorRole\":\"ADMIN\",\"reason\":\"取消\"}";
        mockMvc.perform(post("/interviews/" + id + "/cancel")
                .contentType(MediaType.APPLICATION_JSON).content(cancel))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 已取消的面试不得再改期
        mockMvc.perform(post("/interviews/" + id + "/reschedule")
                .contentType(MediaType.APPLICATION_JSON).content(reschedule))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INTERVIEW_NOT_ACTIVE"));
    }
}
