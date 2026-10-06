package com.github.huchangjun118.interviewschedulingjava.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * 统一时间轴（UTC Instant）上的时间段，左闭右开 [start, end)。
 * 首尾相接不算冲突。
 */
public final class TimeWindow {
    private final Instant start;
    private final Instant end;

    @JsonCreator
    public TimeWindow(@JsonProperty("start") Instant start, @JsonProperty("end") Instant end) {
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("时段结束时间必须晚于开始时间: " + start + " ~ " + end);
        }
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }

    /** 左闭右开区间重叠判定，首尾相接返回 false。 */
    public boolean overlaps(TimeWindow other) {
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeWindow that)) return false;
        return start.equals(that.start) && end.equals(that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public String toString() {
        return "[" + start + " ~ " + end + ")";
    }
}
