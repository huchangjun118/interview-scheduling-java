package com.github.huchangjun118.interviewschedulingjava.service;

/**
 * 越权操作异常。
 */
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException(String message) {
        super(message);
    }
}
