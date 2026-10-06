package com.github.huchangjun118.interviewschedulingjava.exception;

import com.github.huchangjun118.interviewschedulingjava.domain.TimeWindow;

import java.util.List;

/**
 * 排期冲突，携带可用的替代时段。
 */
public class SchedulingConflictException extends RuntimeException {
    private final List<TimeWindow> alternatives;

    public SchedulingConflictException(String message, List<TimeWindow> alternatives) {
        super(message);
        this.alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
    }

    public List<TimeWindow> getAlternatives() {
        return alternatives;
    }
}
