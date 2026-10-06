package com.github.huchangjun118.interviewschedulingjava;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 排期相关配置项。
 */
@Component
@ConfigurationProperties(prefix = "scheduling")
public class ScheduleProperties {

    /**
     * 取消面试的最小提前量（分钟）。
     */
    private long minCancelLeadMinutes = 120;

    /**
     * 持久化快照文件路径。
     */
    private String persistenceFile = "data/scheduling-state.json";

    /**
     * 是否启用重启恢复（加载快照）。
     */
    private boolean persistenceEnabled = true;

    public long getMinCancelLeadMinutes() {
        return minCancelLeadMinutes;
    }

    public void setMinCancelLeadMinutes(long minCancelLeadMinutes) {
        this.minCancelLeadMinutes = minCancelLeadMinutes;
    }

    public String getPersistenceFile() {
        return persistenceFile;
    }

    public void setPersistenceFile(String persistenceFile) {
        this.persistenceFile = persistenceFile;
    }

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    public void setPersistenceEnabled(boolean persistenceEnabled) {
        this.persistenceEnabled = persistenceEnabled;
    }
}
