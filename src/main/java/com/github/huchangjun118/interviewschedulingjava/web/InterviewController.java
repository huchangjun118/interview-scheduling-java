package com.github.huchangjun118.interviewschedulingjava.web;

import com.github.huchangjun118.interviewschedulingjava.domain.Interview;
import com.github.huchangjun118.interviewschedulingjava.domain.Interviewer;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import com.github.huchangjun118.interviewschedulingjava.dto.CancelRequest;
import com.github.huchangjun118.interviewschedulingjava.dto.RescheduleRequest;
import com.github.huchangjun118.interviewschedulingjava.dto.ScheduleRequest;
import com.github.huchangjun118.interviewschedulingjava.dto.ScheduleResult;
import com.github.huchangjun118.interviewschedulingjava.service.InterviewService;
import com.github.huchangjun118.interviewschedulingjava.service.PermissionService;
import com.github.huchangjun118.interviewschedulingjava.service.PersistenceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 面试安排、改期、取消与查询接口。
 */
@RestController
@RequestMapping("/interviews")
public class InterviewController {

    private final InterviewService interviewService;
    private final PermissionService permissionService;
    private final PersistenceService persistenceService;

    public InterviewController(InterviewService interviewService,
                               PermissionService permissionService,
                               PersistenceService persistenceService) {
        this.interviewService = interviewService;
        this.permissionService = permissionService;
        this.persistenceService = persistenceService;
    }

    @PostMapping("/interviewers")
    public Interviewer registerInterviewer(@RequestBody Map<String, String> body) {
        Interviewer i = interviewService.registerInterviewer(body.get("id"), body.get("name"));
        persistenceService.snapshot();
        return i;
    }

    @PostMapping
    public ResponseEntity<ScheduleResult> schedule(@RequestBody ScheduleRequest req) {
        ScheduleResult result = interviewService.schedule(req);
        if (result.isSuccess()) {
            persistenceService.snapshot();
            return ResponseEntity.ok(result);
        }
        if (result.isConflict()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }
        return ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/{id}/reschedule")
    public ResponseEntity<ScheduleResult> reschedule(@PathVariable String id,
                                                     @RequestBody RescheduleRequest req) {
        Role role = permissionService.parseRole(req.getOperatorRole());
        ScheduleResult result = interviewService.reschedule(id,
                req.getNewWindow().startUtc(), req.getNewWindow().endUtc(),
                req.getOperatorId(), role, req.getReason());
        if (result.isSuccess()) {
            persistenceService.snapshot();
            return ResponseEntity.ok(result);
        }
        if (result.isConflict()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }
        return ResponseEntity.badRequest().body(result);
    }

    @PostMapping("/{id}/cancel")
    public Interview cancel(@PathVariable String id, @RequestBody CancelRequest req) {
        Role role = permissionService.parseRole(req.getOperatorRole());
        Interview interview = interviewService.cancel(id, req.getOperatorId(), role, req.getReason());
        persistenceService.snapshot();
        return interview;
    }

    @GetMapping
    public List<Interview> list() {
        return interviewService.listAll();
    }

    @GetMapping("/{id}")
    public Interview get(@PathVariable String id) {
        return interviewService.get(id);
    }
}
