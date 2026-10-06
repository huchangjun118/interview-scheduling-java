package com.github.huchangjun118.interviewschedulingjava.service;

import com.github.huchangjun118.interviewschedulingjava.domain.AuditAction;
import com.github.huchangjun118.interviewschedulingjava.domain.Candidate;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import com.github.huchangjun118.interviewschedulingjava.domain.Stage;
import com.github.huchangjun118.interviewschedulingjava.domain.StageTransitionRules;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 候选人管理与阶段流转。
 */
@Service
public class CandidateService {

    private final Map<String, Candidate> candidates = new ConcurrentHashMap<>();
    private final AuditService auditService;

    public CandidateService(AuditService auditService) {
        this.auditService = auditService;
    }

    public Candidate register(String id, String name) {
        Candidate c = new Candidate(id, name, Stage.APPLIED);
        candidates.put(id, c);
        return c;
    }

    public Candidate get(String id) {
        Candidate c = candidates.get(id);
        if (c == null) {
            throw new SchedulingException("CANDIDATE_NOT_FOUND", "候选人不存在: " + id);
        }
        return c;
    }

    public List<Candidate> listAll() {
        return new ArrayList<>(candidates.values());
    }

    /**
     * 推进候选人阶段，只允许按既定顺序单步推进。
     * 非法跳转会被拒绝并记录审计，异常信息说明当前阶段与目标阶段的冲突原因。
     */
    public synchronized Candidate advanceStage(String candidateId, Stage target,
                                               String operatorId, Role operatorRole) {
        Candidate c = get(candidateId);
        Stage current = c.getStage();
        if (!StageTransitionRules.canAdvance(current, target)) {
            String reason = StageTransitionRules.explainViolation(current, target);
            auditService.record(AuditAction.STAGE_ADVANCE_REJECTED, operatorId, operatorRole,
                    candidateId, reason);
            throw new SchedulingException("ILLEGAL_STAGE_TRANSITION", reason);
        }
        c.setStage(target);
        auditService.record(AuditAction.STAGE_ADVANCE, operatorId, operatorRole,
                candidateId, "阶段由 " + current + " 推进到 " + target);
        return c;
    }

    /**
     * 供持久化恢复使用。
     */
    public void restore(List<Candidate> restored) {
        candidates.clear();
        for (Candidate c : restored) {
            candidates.put(c.getId(), c);
        }
    }

    public void clear() {
        candidates.clear();
    }
}
