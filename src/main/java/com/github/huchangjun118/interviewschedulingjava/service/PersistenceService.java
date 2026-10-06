package com.github.huchangjun118.interviewschedulingjava.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.huchangjun118.interviewschedulingjava.ScheduleProperties;
import com.github.huchangjun118.interviewschedulingjava.domain.AuditRecord;
import com.github.huchangjun118.interviewschedulingjava.domain.AvailabilitySlot;
import com.github.huchangjun118.interviewschedulingjava.domain.Candidate;
import com.github.huchangjun118.interviewschedulingjava.domain.Interview;
import com.github.huchangjun118.interviewschedulingjava.domain.Interviewer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * 持久化与恢复：将候选人、面试官、可用时段、面试与审计记录快照到本地文件，
 * 重启后整体恢复，保证已确认面试与已占用时段一致（占用关系由面试集合推导）。
 * 写文件采用"临时文件 + 原子移动"，避免半截文件。
 */
@Service
public class PersistenceService {

    private static final Logger log = LoggerFactory.getLogger(PersistenceService.class);

    private final ScheduleProperties properties;
    private final CandidateService candidateService;
    private final InterviewService interviewService;
    private final AvailabilityService availabilityService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public PersistenceService(ScheduleProperties properties,
                              CandidateService candidateService,
                              InterviewService interviewService,
                              AvailabilityService availabilityService,
                              AuditService auditService,
                              ObjectMapper objectMapper) {
        this.properties = properties;
        this.candidateService = candidateService;
        this.interviewService = interviewService;
        this.availabilityService = availabilityService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * 状态快照结构。
     */
    public record Snapshot(List<Candidate> candidates,
                           List<Interviewer> interviewers,
                           Map<String, List<AvailabilitySlot>> availability,
                           List<Interview> interviews,
                           List<AuditRecord> audits) {
    }

    @PostConstruct
    public void loadOnStartup() {
        if (!properties.isPersistenceEnabled()) {
            return;
        }
        Path file = Path.of(properties.getPersistenceFile());
        if (!Files.exists(file)) {
            return;
        }
        try {
            Snapshot snapshot = objectMapper.readValue(file.toFile(), Snapshot.class);
            candidateService.restore(snapshot.candidates() == null ? List.of() : snapshot.candidates());
            interviewService.restore(snapshot.interviews() == null ? List.of() : snapshot.interviews(),
                    snapshot.interviewers() == null ? List.of() : snapshot.interviewers());
            availabilityService.restore(snapshot.availability() == null ? Map.of() : snapshot.availability());
            auditService.restore(snapshot.audits() == null ? List.of() : snapshot.audits());
            log.info("已从 {} 恢复排期状态", file);
        } catch (IOException e) {
            log.error("恢复排期状态失败: {}", e.getMessage());
            throw new IllegalStateException("无法从快照恢复排期状态: " + file, e);
        }
    }

    @PreDestroy
    public void saveOnShutdown() {
        snapshot();
    }

    /**
     * 立即快照当前状态。每次成功变更后由调用方触发。
     */
    public synchronized void snapshot() {
        if (!properties.isPersistenceEnabled()) {
            return;
        }
        Path file = Path.of(properties.getPersistenceFile());
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Map<String, List<AvailabilitySlot>> availability = new java.util.HashMap<>();
            for (Candidate c : candidateService.listAll()) {
                availability.put(c.getId(), availabilityService.slotsOf(c.getId()));
            }
            for (Interviewer i : interviewService.listInterviewers()) {
                availability.put(i.getId(), availabilityService.slotsOf(i.getId()));
            }
            Snapshot snapshot = new Snapshot(
                    candidateService.listAll(),
                    interviewService.listInterviewers(),
                    availability,
                    interviewService.listAll(),
                    auditService.listAll());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), snapshot);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("快照排期状态失败: {}", e.getMessage());
            throw new IllegalStateException("快照排期状态失败", e);
        }
    }
}
