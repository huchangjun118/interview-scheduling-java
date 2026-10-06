package com.github.huchangjun118.interviewschedulingjava.exception;

/**
 * 越权操作。
 */
public class PermissionDeniedException extends RuntimeException {
    public PermissionDeniedException(String message) {
        super(message);
    }
}
