package com.github.huchangjun118.interviewschedulingjava.service;

import com.github.huchangjun118.interviewschedulingjava.domain.AvailabilitySlot;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 可用时段维护与冲突检测。
 * 所有时段在服务端统一以 UTC Instant 表示，重叠判定采用半开区间 [start, end)，
 * 因此首尾相接的时段不构成冲突。
 */
@Service
public class AvailabilityService {

    private final Map<String, List<AvailabilitySlot>> slotsByOwner = new ConcurrentHashMap<>();

    /**
     * 半开区间重叠判定：start < other.end && other.start < end。
     * 首尾相接（end == other.start）不算冲突。
     */
    public static boolean overlaps(Instant start, Instant end, Instant otherStart, Instant otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }

    public synchronized AvailabilitySlot addSlot(String ownerId, Instant startUtc, Instant endUtc) {
        validate(startUtc, endUtc);
        AvailabilitySlot slot = new AvailabilitySlot(ownerId, startUtc, endUtc);
        slotsByOwner.computeIfAbsent(ownerId, k -> new CopyOnWriteArrayList<>()).add(slot);
        return slot;
    }

    public synchronized boolean removeSlot(String ownerId, Instant startUtc, Instant endUtc) {
        List<AvailabilitySlot> slots = slotsByOwner.get(ownerId);
        if (slots == null) {
            return false;
        }
        return slots.removeIf(s -> s.getStart().equals(startUtc) && s.getEnd().equals(endUtc));
    }

    public List<AvailabilitySlot> slotsOf(String ownerId) {
        return new ArrayList<>(slotsByOwner.getOrDefault(ownerId, List.of()));
    }

    /**
     * 判断某人在 [startUtc, endUtc) 是否有可用时段覆盖。
     */
    public boolean isAvailable(String ownerId, Instant startUtc, Instant endUtc) {
        for (AvailabilitySlot s : slotsOf(ownerId)) {
            if (!s.getStart().isAfter(startUtc) && !s.getEnd().isBefore(endUtc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 找出某人在指定范围内的可用时段（用于推荐替代时段）。
     */
    public List<AvailabilitySlot> findAvailableBetween(String ownerId, Instant from, Instant to) {
        List<AvailabilitySlot> result = new ArrayList<>();
        for (AvailabilitySlot s : slotsOf(ownerId)) {
            if (overlaps(from, to, s.getStart(), s.getEnd())) {
                result.add(s);
            }
        }
        return result;
    }

    private void validate(Instant start, Instant end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new SchedulingException("INVALID_WINDOW", "时段非法：开始必须早于结束");
        }
    }

    public void restore(Map<String, List<AvailabilitySlot>> restored) {
        slotsByOwner.clear();
        restored.forEach((k, v) -> slotsByOwner.put(k, new CopyOnWriteArrayList<>(v)));
    }

    public void clear() {
        slotsByOwner.clear();
    }
}
