package com.github.huchangjun118.interviewschedulingjava.tests.support;

import com.github.huchangjun118.interviewschedulingjava.config.SchedulingProperties;
import com.github.huchangjun118.interviewschedulingjava.service.AuditService;
import com.github.huchangjun118.interviewschedulingjava.service.AvailabilityService;
import com.github.huchangjun118.interviewschedulingjava.service.SchedulingService;
import com.github.huchangjun118.interviewschedulingjava.service.StageService;
import com.github.huchangjun118.interviewschedulingjava.store.SchedulingStore;

import java.nio.file.Path;
import java.time.Instant;

/**
 * 测试环境：独立的存储文件 + 可控时钟 + 全套服务。
 */
public class TestEnv implements AutoCloseable {

    public static final Instant T0 = Instant.parse("2030-01-01T00:00:00Z");

    public final SchedulingProperties properties;
    public final SchedulingStore store;
    public final AuditService auditService;
    public final AvailabilityService availabilityService;
    public final StageService stageService;
    public final SchedulingService schedulingService;
    public final MutableClock clock;

    public TestEnv(Path storageFile) {
        this.properties = new SchedulingProperties();
        this.properties.setStorageFile(storageFile.toString());
        this.store = new SchedulingStore(properties);
        this.store.init();
        this.auditService = new AuditService(store);
        this.availabilityService = new AvailabilityService(store, auditService);
        this.stageService = new StageService(store, auditService);
        this.clock = new MutableClock(T0);
        this.schedulingService = new SchedulingService(store, availabilityService, auditService, properties, clock);
    }

    /** 模拟重启：用同一存储文件重建一套服务。 */
    public TestEnv restart() {
        return new TestEnv(Path.of(properties.getStorageFile()));
    }

    @Override
    public void close() {
    }
}
