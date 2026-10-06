package com.github.huchangjun118.interviewschedulingjava.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 带时区的时段入参，服务端统一换算到 UTC 时间轴。
 */
public class TimeWindow {

    private String start;
    private String end;

    public TimeWindow() {
    }

    public TimeWindow(String start, String end) {
        this.start = start;
        this.end = end;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    /**
     * 将带偏移量的时间解析为 UTC Instant。
     */
    public static Instant parseToUtc(String text) {
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (java.time.format.DateTimeParseException e) {
            // 允许不带偏移量的写法，按 UTC 解释
            return java.time.LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
        }
    }

    public Instant startUtc() {
        return parseToUtc(start);
    }

    public Instant endUtc() {
        return parseToUtc(end);
    }
}
