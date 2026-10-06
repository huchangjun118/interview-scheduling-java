package com.github.huchangjun118.interviewschedulingjava.domain;

import java.time.Instant;

/**
 * 审计记录：任何安排、改期、取消、阶段流转与越权拒绝都可追溯。
 */
public class AuditRecord {
    private String id;
    private Instant occurredAt;
    private String operatorId;
    private Role operatorRole;
    private String action;
    private String targetType;
    private String targetId;
    private boolean allowed;
    private String reason;

    public AuditRecord() {
    }

    public AuditRecord(String id, Instant occurredAt, String operatorId, Role operatorRole,
                       String action, String targetType, String targetId,
                       boolean allowed, String reason) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.operatorId = operatorId;
        this.operatorRole = operatorRole;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.allowed = allowed;
        this.reason = reason;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public Role getOperatorRole() {
        return operatorRole;
    }

    public void setOperatorRole(Role operatorRole) {
        this.operatorRole = operatorRole;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
