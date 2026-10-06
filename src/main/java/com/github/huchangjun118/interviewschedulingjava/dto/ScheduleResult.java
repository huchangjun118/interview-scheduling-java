package com.github.huchangjun118.interviewschedulingjava.dto;

import com.github.huchangjun118.interviewschedulingjava.domain.Interview;

import java.time.Instant;
import java.util.List;

/**
 * 排期结果：成功返回面试，冲突返回原因与替代时段。
 */
public class ScheduleResult {

    private boolean success;
    private boolean conflict;
    private String message;
    private Interview interview;
    private List<TimeWindowView> alternatives;

    public static ScheduleResult ok(Interview interview, String message) {
        ScheduleResult r = new ScheduleResult();
        r.success = true;
        r.conflict = false;
        r.message = message;
        r.interview = interview;
        return r;
    }

    public static ScheduleResult conflict(String message, List<TimeWindowView> alternatives) {
        ScheduleResult r = new ScheduleResult();
        r.success = false;
        r.conflict = true;
        r.message = message;
        r.alternatives = alternatives;
        return r;
    }

    public static ScheduleResult failure(String message) {
        ScheduleResult r = new ScheduleResult();
        r.success = false;
        r.conflict = false;
        r.message = message;
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isConflict() {
        return conflict;
    }

    public String getMessage() {
        return message;
    }

    public Interview getInterview() {
        return interview;
    }

    public List<TimeWindowView> getAlternatives() {
        return alternatives;
    }

    /**
     * 时段视图（UTC）。
     */
    public record TimeWindowView(Instant start, Instant end) {
    }
}
