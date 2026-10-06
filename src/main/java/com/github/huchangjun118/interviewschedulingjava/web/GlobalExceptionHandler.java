package com.github.huchangjun118.interviewschedulingjava.web;

import com.github.huchangjun118.interviewschedulingjava.service.PermissionDeniedException;
import com.github.huchangjun118.interviewschedulingjava.service.SchedulingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一异常响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SchedulingException.class)
    public ResponseEntity<Map<String, Object>> handleScheduling(SchedulingException e) {
        return build(HttpStatus.BAD_REQUEST, e.getCode(), e.getMessage());
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<Map<String, Object>> handlePermission(PermissionDeniedException e) {
        return build(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
