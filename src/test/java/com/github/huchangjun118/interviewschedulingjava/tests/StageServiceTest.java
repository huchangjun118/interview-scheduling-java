package com.github.huchangjun118.interviewschedulingjava.tests;

import com.github.huchangjun118.interviewschedulingjava.domain.AuditRecord;
import com.github.huchangjun118.interviewschedulingjava.domain.Candidate;
import com.github.huchangjun118.interviewschedulingjava.domain.RecruitmentStage;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import com.github.huchangjun118.interviewschedulingjava.exception.PermissionDeniedException;
import com.github.huchangjun118.interviewschedulingjava.exception.StageTransitionException;
import com.github.huchangjun118.interviewschedulingjava.tests.support.TestEnv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段流转：顺序推进、非法跳转、越级、回退、越权。
 */
class StageServiceTest {

    @TempDir
    Path tempDir;

    private TestEnv newEnv() {
        return new TestEnv(tempDir.resolve("state.json"));
    }

    @Test
    void advanceInOrderSucceeds() {
        try (TestEnv env = newEnv()) {
            env.stageService.register("c1", "张三");
            Candidate c = env.stageService.advance("r1", Role.RECRUITER, "c1", RecruitmentStage.SCREENING);
            assertEquals(RecruitmentStage.SCREENING, c.getStage());
            c = env.stageService.advance("admin", Role.ADMIN, "c1", RecruitmentStage.PHONE_INTERVIEW);
            assertEquals(RecruitmentStage.PHONE_INTERVIEW, c.getStage());
        }
    }

    @Test
    void skipStageRejectedWithReason() {
        try (TestEnv env = newEnv()) {
            env.stageService.register("c1", "张三");
            StageTransitionException e = assertThrows(StageTransitionException.class,
                    () -> env.stageService.advance("r1", Role.RECRUITER, "c1", RecruitmentStage.OFFER));
            assertEquals(RecruitmentStage.APPLIED, e.getCurrent());
            assertEquals(RecruitmentStage.OFFER, e.getTarget());
            assertTrue(e.getMessage().contains("APPLIED"));
            assertTrue(e.getMessage().contains("OFFER"));
            assertTrue(e.getMessage().contains("越级"));
        }
    }

    @Test
    void backwardTransitionRejected() {
        try (TestEnv env = newEnv()) {
            env.stageService.register("c1", "张三");
            env.stageService.advance("r1", Role.RECRUITER, "c1", RecruitmentStage.SCREENING);
            StageTransitionException e = assertThrows(StageTransitionException.class,
                    () -> env.stageService.advance("r1", Role.RECRUITER, "c1", RecruitmentStage.APPLIED));
            assertTrue(e.getMessage().contains("回退"));
        }
    }

    @Test
    void sameStageRejected() {
        try (TestEnv env = newEnv()) {
            env.stageService.register("c1", "张三");
            assertThrows(StageTransitionException.class,
                    () -> env.stageService.advance("r1", Role.RECRUITER, "c1", RecruitmentStage.APPLIED));
        }
    }

    @Test
    void candidateRoleDeniedAndAudited() {
        try (TestEnv env = newEnv()) {
            env.stageService.register("c1", "张三");
            assertThrows(PermissionDeniedException.class,
                    () -> env.stageService.advance("c1", Role.CANDIDATE, "c1", RecruitmentStage.SCREENING));
            // 阶段未被推进
            assertEquals(RecruitmentStage.APPLIED, env.stageService.get("c1").getStage());
            // 越权已记录：操作人与被拒原因
            boolean audited = env.auditService.list().stream().anyMatch(a ->
                    !a.isAllowed()
                            && "c1".equals(a.getOperatorId())
                            && a.getOperatorRole() == Role.CANDIDATE
                            && "STAGE_ADVANCE".equals(a.getAction())
                            && a.getReason() != null && a.getReason().contains("无权"));
            assertTrue(audited, "越权拒绝应被审计记录");
            AuditRecord record = env.auditService.list().stream()
                    .filter(a -> !a.isAllowed()).findFirst().orElseThrow();
            assertEquals("c1", record.getTargetId());
        }
    }
}
