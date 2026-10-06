package com.github.huchangjun118.interviewschedulingjava.service;

/**
 * 排期业务异常。
 */
public class SchedulingException extends RuntimeException {

    private final String code;

    public SchedulingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
