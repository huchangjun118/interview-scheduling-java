package com.github.huchangjun118.interviewschedulingjava.domain;

/**
 * 面试官或候选人登记的可用时段（已换算到统一时间轴）。
 */
public class AvailabilitySlot {
    private String id;
    private String ownerId;
    private Role ownerRole;
    private TimeWindow window;

    public AvailabilitySlot() {
    }

    public AvailabilitySlot(String id, String ownerId, Role ownerRole, TimeWindow window) {
        this.id = id;
        this.ownerId = ownerId;
        this.ownerRole = ownerRole;
        this.window = window;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public Role getOwnerRole() {
        return ownerRole;
    }

    public void setOwnerRole(Role ownerRole) {
        this.ownerRole = ownerRole;
    }

    public TimeWindow getWindow() {
        return window;
    }

    public void setWindow(TimeWindow window) {
        this.window = window;
    }
}
