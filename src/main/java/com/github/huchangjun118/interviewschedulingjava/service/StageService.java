package com.github.huchangjun118.interviewschedulingjava.service;

import com.github.huchangjun118.interviewschedulingjava.domain.Candidate;
import com.github.huchangjun118.interviewschedulingjava.domain.RecruitmentStage;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import com.github.huchangjun118.interviewschedulingjava.exception.PermissionDeniedException;
import com.github.huchangjun118.interviewschedulingjava.exception.ResourceNotFoundException;
import com.github.huchangjun118.interviewschedulingjava.exception.StageTransitionException;
import com.github.huchangjun118.interviewschedulingjava.store.SchedulingStore;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 候选人阶段流转服务。
 */
@Service
public class StageService {

    private final SchedulingStore store;
    private final AuditService auditService;

    public StageService(SchedulingStore store, AuditService auditService) {
        this.store = store;
        this.auditService = auditService;
    }

    public Candidate register(String candidateId, String name) {
        return store.mutate(() -> {
            Candidate candidate = new Candidate(candidateId, name);
            store.candidates().put(candidateId, candidate);
            return candidate;
        });
    }

    public Candidate get(String candidateId) {
        Candidate candidate = store.candidates().get(candidateId);
        if (candidate == null) {
            throw new ResourceNotFoundException("候选人不存在: " + candidateId);
        }
        return candidate;
    }

    public List<Candidate> list() {
        return store.candidates().values().stream()
                .sorted(Comparator.comparing(Candidate::getId))
                .toList();
    }

    /** 推进候选人到目标阶段，非法跳转/越级将被拒绝并说明原因。 */
    public Candidate advance(String operatorId, Role operatorRole, String candidateId, RecruitmentStage target) {
        // 权限：仅招聘官与招聘管理员可推进阶段
        if (operatorRole != Role.RECRUITER && operatorRole != Role.ADMIN) {
            String reason = "角色 " + operatorRole + " 无权推进候选人阶段，仅 RECRUITER/ADMIN 允许";
            auditService.record(operatorId, operatorRole, "STAGE_ADVANCE", "CANDIDATE", candidateId, false, reason);
            throw new PermissionDeniedException(reason);
        }
        return store.mutate(() -> {
            Candidate candidate = store.candidates().get(candidateId);
            if (candidate == null) {
                throw new ResourceNotFoundException("候选人不存在: " + candidateId);
            }
            RecruitmentStage current = candidate.getStage();
            String error = current.transitionError(target);
            if (error != null) {
                auditService.record(operatorId, operatorRole, "STAGE_ADVANCE", "CANDIDATE", candidateId, false, error);
                throw new StageTransitionException(current, target, error);
            }
            candidate.setStage(target);
            auditService.record(operatorId, operatorRole, "STAGE_ADVANCE", "CANDIDATE", candidateId, true,
                    current + " -> " + target);
            return candidate;
        });
    }
}
