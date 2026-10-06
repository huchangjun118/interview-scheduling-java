package com.github.huchangjun118.interviewschedulingjava.service;

import com.github.huchangjun118.interviewschedulingjava.domain.AuditAction;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 角色权限校验：越权请求被拒绝并记录操作人与被拒原因。
 */
@Service
public class PermissionService {

    private final AuditService auditService;

    public PermissionService(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * 解析角色字符串，非法角色视为越权。
     */
    public Role parseRole(String text) {
        if (text == null) {
            throw new PermissionDeniedException("缺少操作人角色");
        }
        try {
            return Role.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new PermissionDeniedException("未知角色: " + text);
        }
    }

    /**
     * 要求操作人角色在允许集合内，否则记录审计并抛越权异常。
     */
    public void require(String operatorId, Role role, Set<Role> allowed, String action, String targetId) {
        if (operatorId == null || operatorId.isBlank()) {
            auditService.record(AuditAction.PERMISSION_DENIED, operatorId, role, targetId,
                    "缺少操作人标识，拒绝执行 " + action);
            throw new PermissionDeniedException("缺少操作人标识");
        }
        if (!allowed.contains(role)) {
            String reason = "角色 " + role + " 无权执行 " + action + "，允许的角色: " + allowed;
            auditService.record(AuditAction.PERMISSION_DENIED, operatorId, role, targetId, reason);
            throw new PermissionDeniedException(reason);
        }
    }
}
