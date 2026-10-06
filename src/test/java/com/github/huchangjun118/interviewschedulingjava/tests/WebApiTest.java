package com.github.huchangjun118.interviewschedulingjava.tests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST 端到端：排期、冲突响应、阶段流转拒绝、越权拒绝、审计查询。
 */
@SpringBootTest(properties = "scheduling.storage-file=target/test-state/web-${random.uuid}.json")
@AutoConfigureMockMvc
class WebApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void fullFlowOverRest() throws Exception {
        // 注册候选人
        mvc.perform(post("/api/candidates").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":\"c1\",\"name\":\"张三\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("APPLIED"));

        // 越级流转被拒绝，响应说明当前与目标阶段的冲突
        mvc.perform(post("/api/candidates/c1/stage").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"r1\",\"operatorRole\":\"RECRUITER\",\"targetStage\":\"OFFER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_TRANSITION_REJECTED"))
                .andExpect(jsonPath("$.currentStage").value("APPLIED"))
                .andExpect(jsonPath("$.targetStage").value("OFFER"));

        // 候选人越权推进自己的阶段被拒绝
        mvc.perform(post("/api/candidates/c1/stage").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"c1\",\"operatorRole\":\"CANDIDATE\",\"targetStage\":\"SCREENING\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        // 登记可用时段（带时区）
        mvc.perform(post("/api/availability").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"r1\",\"operatorRole\":\"RECRUITER\",\"ownerId\":\"r1\","
                                + "\"start\":\"2030-01-01T18:00:00+08:00\",\"end\":\"2030-01-01T20:00:00+08:00\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/availability").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"c1\",\"operatorRole\":\"CANDIDATE\",\"ownerId\":\"c1\","
                                + "\"start\":\"2030-01-01T09:00:00Z\",\"end\":\"2030-01-01T13:00:00Z\"}"))
                .andExpect(status().isOk());

        // 候选人越权排期被拒绝
        mvc.perform(post("/api/interviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestKey\":\"req-x\",\"operatorId\":\"c1\",\"operatorRole\":\"CANDIDATE\","
                                + "\"candidateId\":\"c1\",\"interviewerId\":\"r1\",\"stage\":\"APPLIED\","
                                + "\"start\":\"2030-01-01T10:00:00Z\",\"end\":\"2030-01-01T11:00:00Z\"}"))
                .andExpect(status().isForbidden());

        // 正常排期：上海 18:00-19:00 == UTC 10:00-11:00
        mvc.perform(post("/api/interviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestKey\":\"req-1\",\"operatorId\":\"r1\",\"operatorRole\":\"RECRUITER\","
                                + "\"candidateId\":\"c1\",\"interviewerId\":\"r1\",\"stage\":\"APPLIED\","
                                + "\"start\":\"2030-01-01T18:00:00+08:00\",\"end\":\"2030-01-01T19:00:00+08:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.window.start").value("2030-01-01T10:00:00Z"));

        // 幂等：重复提交返回同一场面试
        mvc.perform(post("/api/interviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestKey\":\"req-1\",\"operatorId\":\"r1\",\"operatorRole\":\"RECRUITER\","
                                + "\"candidateId\":\"c1\",\"interviewerId\":\"r1\",\"stage\":\"APPLIED\","
                                + "\"start\":\"2030-01-01T18:00:00+08:00\",\"end\":\"2030-01-01T19:00:00+08:00\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/interviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // 冲突：同一面试官重叠时段，返回 409 与替代时段
        mvc.perform(post("/api/candidates").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":\"c2\",\"name\":\"李四\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/availability").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"c2\",\"operatorRole\":\"CANDIDATE\",\"ownerId\":\"c2\","
                                + "\"start\":\"2030-01-01T09:00:00Z\",\"end\":\"2030-01-01T13:00:00Z\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/interviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestKey\":\"req-2\",\"operatorId\":\"r1\",\"operatorRole\":\"RECRUITER\","
                                + "\"candidateId\":\"c2\",\"interviewerId\":\"r1\",\"stage\":\"APPLIED\","
                                + "\"start\":\"2030-01-01T10:30:00Z\",\"end\":\"2030-01-01T11:30:00Z\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULING_CONFLICT"))
                .andExpect(jsonPath("$.alternatives", hasSize(greaterThanOrEqualTo(1))));

        // 审计：越权与排期均可追溯
        mvc.perform(get("/api/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));
    }
}
