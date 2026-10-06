package com.github.huchangjun118.interviewschedulingjava.web;

import com.github.huchangjun118.interviewschedulingjava.domain.AuditRecord;
import com.github.huchangjun118.interviewschedulingjava.service.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审计查询接口。
 */
@RestController
@RequestMapping("/audits")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditRecord> list(@RequestParam(required = false) String targetId) {
        if (targetId != null) {
            return auditService.listByTarget(targetId);
        }
        return auditService.listAll();
    }
}
