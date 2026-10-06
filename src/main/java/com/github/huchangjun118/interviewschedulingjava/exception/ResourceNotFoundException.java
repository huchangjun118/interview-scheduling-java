package com.github.huchangjun118.interviewschedulingjava.exception;

/**
 * 资源不存在。
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
