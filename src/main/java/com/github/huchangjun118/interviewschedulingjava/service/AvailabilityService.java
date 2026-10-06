package com.github.huchangjun118.interviewschedulingjava.service;

import com.github.huchangjun118.interviewschedulingjava.domain.AvailabilitySlot;
import com.github.huchangjun118.interviewschedulingjava.domain.Interview;
import com.github.huchangjun118.interviewschedulingjava.domain.InterviewStatus;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;
import com.github.huchangjun118.interviewschedulingjava.domain.TimeWindow;
import com.github.huchangjun118.interviewschedulingjava.exception.PermissionDeniedException;
import com.github.huchangjun118.interviewschedulingjava.exception.ResourceNotFoundException;
import com.github.huchangjun118.interviewschedulingjava.store.SchedulingStore;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 可用时段维护与冲突检测。
 */
@Service
public class AvailabilityService {

    private final SchedulingStore store;
    private final AuditService auditService;

    public AvailabilityService(SchedulingStore store, AuditService auditService) {
        this.store = store;
        this.auditService = auditService;
    }

    /** 登记可用时段，入参带时区，统一换算到 UTC 时间轴存储。 */
    public AvailabilitySlot addSlot(String operatorId, Role operatorRole, String ownerId,
                                    ZonedDateTime start, ZonedDateTime end) {
        checkOwnership(operatorId, operatorRole, ownerId, "AVAILABILITY_ADD");
        TimeWindow window = toUtcWindow(start, end);
        return store.mutate(() -> {
            Role ownerRole = store.candidates().containsKey(ownerId) ? Role.CANDIDATE : Role.RECRUITER;
            AvailabilitySlot slot = new AvailabilitySlot(UUID.randomUUID().toString(),
                    ownerId, ownerRole, window);
            store.availabilitySlots().put(slot.getId(), slot);
            auditService.record(operatorId, operatorRole, "AVAILABILITY_ADD", "SLOT", slot.getId(), true,
                    "owner=" + ownerId + " " + window);
            return slot;
        });
    }

    public void removeSlot(String operatorId, Role operatorRole, String slotId) {
        store.mutate(() -> {
            AvailabilitySlot slot = store.availabilitySlots().get(slotId);
            if (slot == null) {
                throw new ResourceNotFoundException("可用时段不存在: " + slotId);
            }
            checkOwnership(operatorId, operatorRole, slot.getOwnerId(), "AVAILABILITY_REMOVE");
            store.availabilitySlots().remove(slotId);
            auditService.record(operatorId, operatorRole, "AVAILABILITY_REMOVE", "SLOT", slotId, true, null);
            return null;
        });
    }

    public List<AvailabilitySlot> slotsOf(String ownerId) {
        return store.availabilitySlots().values().stream()
                .filter(s -> s.getOwnerId().equals(ownerId))
                .sorted(Comparator.comparing(s -> s.getWindow().getStart()))
                .toList();
    }

    /** 判断某参与人（面试官或候选人）在指定时段是否已被面试占用。 */
    public boolean isBooked(String participantId, TimeWindow window, String excludeInterviewId) {
        return store.interviews().values().stream()
                .filter(i -> i.getStatus() == InterviewStatus.SCHEDULED)
                .filter(i -> excludeInterviewId == null || !i.getId().equals(excludeInterviewId))
                .filter(i -> i.getInterviewerId().equals(participantId) || i.getCandidateId().equals(participantId))
                .anyMatch(i -> i.getWindow().overlaps(window));
    }

    /** 判断某参与人在指定时段是否登记了可用时段（存在完全覆盖该时段的登记才算可用）。 */
    public boolean isAvailable(String participantId, TimeWindow window) {
        return store.availabilitySlots().values().stream()
                .filter(s -> s.getOwnerId().equals(participantId))
                .anyMatch(s -> !s.getWindow().getStart().isAfter(window.getStart())
                        && !s.getWindow().getEnd().isBefore(window.getEnd()));
    }

    /** 把带时区的起止时间换算到统一 UTC 时间轴。 */
    public TimeWindow toUtcWindow(ZonedDateTime start, ZonedDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("起止时间不能为空");
        }
        return new TimeWindow(start.toInstant(), end.toInstant());
    }

    private void checkOwnership(String operatorId, Role operatorRole, String ownerId, String action) {
        if (operatorRole == Role.ADMIN) {
            return;
        }
        if ((operatorRole == Role.RECRUITER || operatorRole == Role.CANDIDATE)
                && operatorId.equals(ownerId)) {
            return;
        }
        String reason = "角色 " + operatorRole + " 的操作人 " + operatorId
                + " 无权维护 " + ownerId + " 的可用时段";
        auditService.record(operatorId, operatorRole, action, "SLOT", ownerId, false, reason);
        throw new PermissionDeniedException(reason);
    }
}
