package com.github.huchangjun118.interviewschedulingjava.domain;

import java.time.Instant;

/**
 * 可用时段，内部统一以 UTC Instant 表示。
 */
public class AvailabilitySlot {

    private String ownerId;
    private Instant start;
    private Instant end;

    public AvailabilitySlot() {
    }

    public AvailabilitySlot(String ownerId, Instant start, Instant end) {
        this.ownerId = ownerId;
        this.start = start;
        this.end = end;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public Instant getStart() {
        return start;
    }

    public void setStart(Instant start) {
        this.start = start;
    }

    public Instant getEnd() {
        return end;
    }

    public void setEnd(Instant end) {
        this.end = end;
    }
}
