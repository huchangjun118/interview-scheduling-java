package com.github.huchangjun118.interviewschedulingjava.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.huchangjun118.interviewschedulingjava.config.SchedulingProperties;
import com.github.huchangjun118.interviewschedulingjava.domain.AuditRecord;
import com.github.huchangjun118.interviewschedulingjava.domain.AvailabilitySlot;
import com.github.huchangjun118.interviewschedulingjava.domain.Candidate;
import com.github.huchangjun118.interviewschedulingjava.domain.Interview;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * 内存状态 + JSON 快照持久化。
 * 所有写操作在同一把锁内完成并落盘，保证并发排期互斥与重启恢复一致。
 */
@Component
public class SchedulingStore {

    private static final Logger log = LoggerFactory.getLogger(SchedulingStore.class);

    private final SchedulingProperties properties;
    private final ObjectMapper objectMapper;

    private final Map<String, Candidate> candidates = new ConcurrentHashMap<>();
    private final Map<String, Interview> interviews = new ConcurrentHashMap<>();
    private final Map<String, AvailabilitySlot> availabilitySlots = new ConcurrentHashMap<>();
    private final Map<String, String> requestKeyToInterviewId = new ConcurrentHashMap<>();
    private final List<AuditRecord> auditRecords = new CopyOnWriteArrayList<>();

    private final Object mutationLock = new Object();

    public SchedulingStore(SchedulingProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    public void init() {
        load();
    }

    /** 在排期互斥锁内执行写操作，成功后持久化快照；落盘失败则抛出异常。 */
    public <T> T mutate(Supplier<T> mutation) {
        synchronized (mutationLock) {
            T result = mutation.get();
            persistLocked();
            return result;
        }
    }

    /** 在排期互斥锁内执行读操作（需要与写一致时使用）。 */
    public <T> T readConsistent(Supplier<T> read) {
        synchronized (mutationLock) {
            return read.get();
        }
    }

    public Map<String, Candidate> candidates() {
        return candidates;
    }

    public Map<String, Interview> interviews() {
        return interviews;
    }

    public Map<String, AvailabilitySlot> availabilitySlots() {
        return availabilitySlots;
    }

    public Map<String, String> requestKeyToInterviewId() {
        return requestKeyToInterviewId;
    }

    public List<AuditRecord> auditRecords() {
        return auditRecords;
    }

    public Optional<Interview> findInterviewByRequestKey(String requestKey) {
        if (requestKey == null) {
            return Optional.empty();
        }
        String id = requestKeyToInterviewId.get(requestKey);
        return id == null ? Optional.empty() : Optional.ofNullable(interviews.get(id));
    }

    /** 启动时从快照恢复，保证已确认面试与占用时段一致。 */
    public void load() {
        Path file = Path.of(properties.getStorageFile());
        if (!Files.exists(file)) {
            return;
        }
        synchronized (mutationLock) {
            try {
                Snapshot snapshot = objectMapper.readValue(file.toFile(), Snapshot.class);
                candidates.clear();
                interviews.clear();
                availabilitySlots.clear();
                requestKeyToInterviewId.clear();
                auditRecords.clear();
                if (snapshot.candidates != null) {
                    candidates.putAll(snapshot.candidates);
                }
                if (snapshot.interviews != null) {
                    interviews.putAll(snapshot.interviews);
                }
                if (snapshot.availabilitySlots != null) {
                    availabilitySlots.putAll(snapshot.availabilitySlots);
                }
                if (snapshot.requestKeyToInterviewId != null) {
                    requestKeyToInterviewId.putAll(snapshot.requestKeyToInterviewId);
                }
                if (snapshot.auditRecords != null) {
                    auditRecords.addAll(snapshot.auditRecords);
                }
                log.info("Recovered scheduling state from {}: {} interviews, {} candidates",
                        file, interviews.size(), candidates.size());
            } catch (IOException e) {
                throw new IllegalStateException("无法从快照恢复排期状态: " + file, e);
            }
        }
    }

    /** 落盘：先写临时文件再原子移动，避免半截文件。 */
    private void persistLocked() {
        Path file = Path.of(properties.getStorageFile());
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Snapshot snapshot = new Snapshot();
            snapshot.candidates = new LinkedHashMap<>(candidates);
            snapshot.interviews = new LinkedHashMap<>(interviews);
            snapshot.availabilitySlots = new LinkedHashMap<>(availabilitySlots);
            snapshot.requestKeyToInterviewId = new LinkedHashMap<>(requestKeyToInterviewId);
            snapshot.auditRecords = new ArrayList<>(auditRecords);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            objectMapper.writeValue(tmp.toFile(), snapshot);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("排期状态持久化失败: " + file, e);
        }
    }

    /** 快照结构。 */
    public static class Snapshot {
        public Map<String, Candidate> candidates;
        public Map<String, Interview> interviews;
        public Map<String, AvailabilitySlot> availabilitySlots;
        public Map<String, String> requestKeyToInterviewId;
        public List<AuditRecord> auditRecords;
    }
}
