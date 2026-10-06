package com.github.huchangjun118.interviewschedulingjava.web;

import com.github.huchangjun118.interviewschedulingjava.domain.TimeWindow;
import com.github.huchangjun118.interviewschedulingjava.exception.InvalidOperationException;
import com.github.huchangjun118.interviewschedulingjava.exception.PermissionDeniedException;
import com.github.huchangjun118.interviewschedulingjava.exception.ResourceNotFoundException;
import com.github.huchangjun118.interviewschedulingjava.exception.SchedulingConflictException;
import com.github.huchangjun118.interviewschedulingjava.exception.StageTransitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一异常响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StageTransitionException.class)
    public ResponseEntity<Map<String, Object>> stageTransition(StageTransitionException e) {
        Map<String, Object> body = body("STAGE_TRANSITION_REJECTED", e.getMessage());
        body.put("currentStage", e.getCurrent().name());
        body.put("targetStage", e.getTarget().name());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(SchedulingConflictException.class)
    public ResponseEntity<Map<String, Object>> schedulingConflict(SchedulingConflictException e) {
        Map<String, Object> body = body("SCHEDULING_CONFLICT", e.getMessage());
        List<Map<String, String>> alternatives = e.getAlternatives().stream()
                .map(this::windowToMap)
                .toList();
        body.put("alternatives", alternatives);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<Map<String, Object>> permissionDenied(PermissionDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body("PERMISSION_DENIED", e.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<Map<String, Object>> invalidOperation(InvalidOperationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body("INVALID_OPERATION", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body("BAD_REQUEST", e.getMessage()));
    }

    private Map<String, Object> body(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return body;
    }

    private Map<String, String> windowToMap(TimeWindow window) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("start", window.getStart().toString());
        map.put("end", window.getEnd().toString());
        return map;
    }
}
