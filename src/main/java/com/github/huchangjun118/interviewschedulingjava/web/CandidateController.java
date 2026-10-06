package com.github.huchangjun118.interviewschedulingjava.web;

import com.github.huchangjun118.interviewschedulingjava.domain.Candidate;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import com.github.huchangjun118.interviewschedulingjava.domain.Stage;
import com.github.huchangjun118.interviewschedulingjava.dto.AdvanceStageRequest;
import com.github.huchangjun118.interviewschedulingjava.service.CandidateService;
import com.github.huchangjun118.interviewschedulingjava.service.PermissionService;
import com.github.huchangjun118.interviewschedulingjava.service.PersistenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 候选人注册与阶段流转接口。
 */
@RestController
@RequestMapping("/candidates")
public class CandidateController {

    private static final Set<Role> ADVANCE_ROLES = EnumSet.of(Role.RECRUITER, Role.ADMIN);

    private final CandidateService candidateService;
    private final PermissionService permissionService;
    private final PersistenceService persistenceService;

    public CandidateController(CandidateService candidateService,
                               PermissionService permissionService,
                               PersistenceService persistenceService) {
        this.candidateService = candidateService;
        this.permissionService = permissionService;
        this.persistenceService = persistenceService;
    }

    @PostMapping
    public Candidate register(@RequestBody Map<String, String> body) {
        Candidate c = candidateService.register(body.get("id"), body.get("name"));
        persistenceService.snapshot();
        return c;
    }

    @GetMapping
    public List<Candidate> list() {
        return candidateService.listAll();
    }

    @GetMapping("/{id}")
    public Candidate get(@PathVariable String id) {
        return candidateService.get(id);
    }

    @PostMapping("/{id}/advance")
    public Candidate advance(@PathVariable String id, @RequestBody AdvanceStageRequest req) {
        Role role = permissionService.parseRole(req.getOperatorRole());
        permissionService.require(req.getOperatorId(), role, ADVANCE_ROLES, "推进候选人阶段", id);
        Stage target;
        try {
            target = Stage.valueOf(req.getTargetStage().trim().toUpperCase());
        } catch (Exception e) {
            throw new com.github.huchangjun118.interviewschedulingjava.service.SchedulingException(
                    "UNKNOWN_STAGE", "未知目标阶段: " + req.getTargetStage());
        }
        Candidate c = candidateService.advanceStage(id, target, req.getOperatorId(), role);
        persistenceService.snapshot();
        return c;
    }
}
