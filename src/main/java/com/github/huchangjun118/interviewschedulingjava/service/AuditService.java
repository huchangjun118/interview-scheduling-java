package com.github.huchangjun118.interviewschedulingjava.service;

import com.github.huchangjun118.interviewschedulingjava.domain.AuditRecord;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import com.github.huchangjun118.interviewschedulingjava.store.SchedulingStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 审计服务：记录一切安排、改期、取消、阶段流转与越权拒绝。
 */
@Service
public class AuditService {

    private final SchedulingStore store;

    public AuditService(SchedulingStore store) {
        this.store = store;
    }

    public void record(String operatorId, Role operatorRole, String action,
                       String targetType, String targetId, boolean allowed, String reason) {
        store.mutate(() -> {
            store.auditRecords().add(new AuditRecord(
                    UUID.randomUUID().toString(), Instant.now(), operatorId, operatorRole,
                    action, targetType, targetId, allowed, reason));
            return null;
        });
    }

    public List<AuditRecord> list() {
        return List.copyOf(store.auditRecords());
    }
}
