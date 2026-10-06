package com.github.huchangjun118.interviewschedulingjava.web;

import com.github.huchangjun118.interviewschedulingjava.domain.Interview;
import com.github.huchangjun118.interviewschedulingjava.service.SchedulingService;
import com.github.huchangjun118.interviewschedulingjava.web.dto.Requests.CancelInterviewRequest;
import com.github.huchangjun118.interviewschedulingjava.web.dto.Requests.RescheduleInterviewRequest;
import com.github.huchangjun118.interviewschedulingjava.web.dto.Requests.ScheduleInterviewRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 面试排期、改期、取消。
 */
@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final SchedulingService schedulingService;

    public InterviewController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @PostMapping
    public Interview schedule(@RequestBody ScheduleInterviewRequest request) {
        return schedulingService.schedule(request.requestKey(), request.operatorId(), request.operatorRole(),
                request.candidateId(), request.interviewerId(), request.stage(),
                request.start(), request.end());
    }

    @PostMapping("/{interviewId}/reschedule")
    public Interview reschedule(@PathVariable String interviewId,
                                @RequestBody RescheduleInterviewRequest request) {
        return schedulingService.reschedule(request.operatorId(), request.operatorRole(),
                interviewId, request.start(), request.end());
    }

    @PostMapping("/{interviewId}/cancel")
    public Interview cancel(@PathVariable String interviewId,
                            @RequestBody CancelInterviewRequest request) {
        return schedulingService.cancel(request.operatorId(), request.operatorRole(),
                interviewId, request.reason());
    }

    @GetMapping
    public List<Interview> list() {
        return schedulingService.list();
    }

    @GetMapping("/{interviewId}")
    public Interview get(@PathVariable String interviewId) {
        return schedulingService.get(interviewId);
    }
}
