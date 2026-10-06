package com.github.huchangjun118.interviewschedulingjava.domain;

/**
 * 审计动作类型。
 */
public enum AuditAction {
    STAGE_ADVANCE,
    STAGE_ADVANCE_REJECTED,
    SCHEDULE,
    SCHEDULE_CONFLICT,
    RESCHEDULE,
    CANCEL,
    CANCEL_REJECTED,
    PERMISSION_DENIED
}
