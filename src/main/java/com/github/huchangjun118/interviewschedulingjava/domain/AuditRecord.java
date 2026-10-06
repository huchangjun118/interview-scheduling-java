package com.github.huchangjun118.interviewschedulingjava.domain;

import java.time.Instant;

/**
 * 审计记录：任何安排、改期、取消、拒绝均可追溯。
 */
public class AuditRecord {

    private String id;
    private AuditAction action;
    private String operatorId;
    private Role operatorRole;
    private String targetId;
    private String reason;
    private Instant occurredAt;

    public AuditRecord() {
    }

    public AuditRecord(String id, AuditAction action, String operatorId, Role operatorRole,
                       String targetId, String reason, Instant occurredAt) {
        this.id = id;
        this.action = action;
        this.operatorId = operatorId;
        this.operatorRole = operatorRole;
        this.targetId = targetId;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setAction(AuditAction action) {
        this.action = action;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public void setOperatorRole(Role operatorRole) {
        this.operatorRole = operatorRole;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public Role getOperatorRole() {
        return operatorRole;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
