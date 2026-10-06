package com.github.huchangjun118.interviewschedulingjava.service;

import com.github.huchangjun118.interviewschedulingjava.domain.AuditAction;
import com.github.huchangjun118.interviewschedulingjava.domain.AuditRecord;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 审计服务：记录全部安排、改期、取消与拒绝事件。
 */
@Service
public class AuditService {

    private final List<AuditRecord> records = new CopyOnWriteArrayList<>();

    public AuditRecord record(AuditAction action, String operatorId, Role operatorRole,
                              String targetId, String reason) {
        AuditRecord r = new AuditRecord(UUID.randomUUID().toString(), action, operatorId,
                operatorRole, targetId, reason, Instant.now());
        records.add(r);
        return r;
    }

    public List<AuditRecord> listAll() {
        return new ArrayList<>(records);
    }

    public List<AuditRecord> listByTarget(String targetId) {
        List<AuditRecord> result = new ArrayList<>();
        for (AuditRecord r : records) {
            if (r.getTargetId() != null && r.getTargetId().equals(targetId)) {
                result.add(r);
            }
        }
        return result;
    }

    /**
     * 供持久化恢复使用。
     */
    public void restore(List<AuditRecord> restored) {
        records.clear();
        records.addAll(restored);
    }

    /**
     * 供测试清理使用。
     */
    public void clear() {
        records.clear();
    }
}
