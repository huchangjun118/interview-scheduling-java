package com.github.huchangjun118.interviewschedulingjava.exception;

/**
 * 违反业务规则的操作（如取消已过最小提前量的面试、操作已结束的面试）。
 */
public class InvalidOperationException extends RuntimeException {
    public InvalidOperationException(String message) {
        super(message);
    }
}
