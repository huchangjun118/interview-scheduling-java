package com.github.huchangjun118.interviewschedulingjava.web;

import com.github.huchangjun118.interviewschedulingjava.domain.Candidate;
import com.github.huchangjun118.interviewschedulingjava.service.StageService;
import com.github.huchangjun118.interviewschedulingjava.web.dto.Requests.AdvanceStageRequest;
import com.github.huchangjun118.interviewschedulingjava.web.dto.Requests.RegisterCandidateRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 候选人登记与阶段流转。
 */
@RestController
@RequestMapping("/api/candidates")
public class CandidateController {

    private final StageService stageService;

    public CandidateController(StageService stageService) {
        this.stageService = stageService;
    }

    @PostMapping
    public Candidate register(@RequestBody RegisterCandidateRequest request) {
        return stageService.register(request.candidateId(), request.name());
    }

    @GetMapping
    public List<Candidate> list() {
        return stageService.list();
    }

    @GetMapping("/{candidateId}")
    public Candidate get(@PathVariable String candidateId) {
        return stageService.get(candidateId);
    }

    @PostMapping("/{candidateId}/stage")
    public Candidate advance(@PathVariable String candidateId, @RequestBody AdvanceStageRequest request) {
        return stageService.advance(request.operatorId(), request.operatorRole(), candidateId, request.targetStage());
    }
}
