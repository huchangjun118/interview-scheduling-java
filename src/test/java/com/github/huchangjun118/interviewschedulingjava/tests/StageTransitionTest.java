package com.github.huchangjun118.interviewschedulingjava.tests;

import com.github.huchangjun118.interviewschedulingjava.domain.AuditAction;
import com.github.huchangjun118.interviewschedulingjava.domain.Candidate;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import com.github.huchangjun118.interviewschedulingjava.domain.Stage;
import com.github.huchangjun118.interviewschedulingjava.domain.StageTransitionRules;
import com.github.huchangjun118.interviewschedulingjava.service.AuditService;
import com.github.huchangjun118.interviewschedulingjava.service.CandidateService;
import com.github.huchangjun118.interviewschedulingjava.service.SchedulingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段流转测试：合法单步推进、越级/回退/原地/终态拒绝及原因说明。
 */
class StageTransitionTest {

    private AuditService auditService;
    private CandidateService candidateService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService();
        candidateService = new CandidateService(auditService);
        candidateService.register("c1", "张三");
    }

    @Test
    void legalAdvanceThroughAllStages() {
        assertEquals(Stage.APPLIED, candidateService.get("c1").getStage());
        candidateService.advanceStage("c1", Stage.SCREENING, "r1", Role.RECRUITER);
        candidateService.advanceStage("c1", Stage.INTERVIEWING, "r1", Role.RECRUITER);
        candidateService.advanceStage("c1", Stage.OFFER, "r1", Role.RECRUITER);
        Candidate c = candidateService.advanceStage("c1", Stage.HIRED, "admin1", Role.ADMIN);
        assertEquals(Stage.HIRED, c.getStage());
        assertEquals(4, auditService.listByTarget("c1").stream()
                .filter(r -> r.getAction() == AuditAction.STAGE_ADVANCE).count());
    }

    @Test
    void skipStageRejectedWithExplanation() {
        SchedulingException e = assertThrows(SchedulingException.class,
                () -> candidateService.advanceStage("c1", Stage.INTERVIEWING, "r1", Role.RECRUITER));
        assertEquals("ILLEGAL_STAGE_TRANSITION", e.getCode());
        assertTrue(e.getMessage().contains("APPLIED"));
        assertTrue(e.getMessage().contains("INTERVIEWING"));
        assertTrue(e.getMessage().contains("SCREENING"), "应说明唯一允许的下一阶段");
        // 阶段未被改变
        assertEquals(Stage.APPLIED, candidateService.get("c1").getStage());
        // 拒绝被审计记录
        assertEquals(1, auditService.listByTarget("c1").stream()
                .filter(r -> r.getAction() == AuditAction.STAGE_ADVANCE_REJECTED).count());
    }

    @Test
    void backwardAndSameStageRejected() {
        candidateService.advanceStage("c1", Stage.SCREENING, "r1", Role.RECRUITER);
        SchedulingException back = assertThrows(SchedulingException.class,
                () -> candidateService.advanceStage("c1", Stage.APPLIED, "r1", Role.RECRUITER));
        assertTrue(back.getMessage().contains("回退"));
        SchedulingException same = assertThrows(SchedulingException.class,
                () -> candidateService.advanceStage("c1", Stage.SCREENING, "r1", Role.RECRUITER));
        assertTrue(same.getMessage().contains("相同"));
    }

    @Test
    void terminalStageRejectsAnyTransition() {
        Candidate c = candidateService.get("c1");
        c.setStage(Stage.HIRED);
        SchedulingException e = assertThrows(SchedulingException.class,
                () -> candidateService.advanceStage("c1", Stage.OFFER, "r1", Role.RECRUITER));
        assertTrue(e.getMessage().contains("终态"));
    }

    @Test
    void rulesExplainViolation() {
        assertTrue(StageTransitionRules.canAdvance(Stage.APPLIED, Stage.SCREENING));
        assertEquals(Stage.OFFER, StageTransitionRules.nextOf(Stage.INTERVIEWING).orElseThrow());
        assertTrue(StageTransitionRules.nextOf(Stage.HIRED).isEmpty());
    }
}
