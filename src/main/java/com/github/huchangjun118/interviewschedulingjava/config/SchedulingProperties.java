package com.github.huchangjun118.interviewschedulingjava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 排期系统配置项。
 */
@ConfigurationProperties(prefix = "scheduling")
public class SchedulingProperties {
    /** 取消/改期的最小提前量，默认 2 小时 */
    private Duration minCancelLeadTime = Duration.ofHours(2);
    /** 状态快照持久化文件路径 */
    private String storageFile = "data/scheduling-state.json";
    /** 冲突时返回的替代时段数量上限 */
    private int maxAlternatives = 3;
    /** 搜索替代时段时的候选时长（分钟），默认同面试时长 */
    private int alternativeSearchDays = 7;

    public Duration getMinCancelLeadTime() {
        return minCancelLeadTime;
    }

    public void setMinCancelLeadTime(Duration minCancelLeadTime) {
        this.minCancelLeadTime = minCancelLeadTime;
    }

    public String getStorageFile() {
        return storageFile;
    }

    public void setStorageFile(String storageFile) {
        this.storageFile = storageFile;
    }

    public int getMaxAlternatives() {
        return maxAlternatives;
    }

    public void setMaxAlternatives(int maxAlternatives) {
        this.maxAlternatives = maxAlternatives;
    }

    public int getAlternativeSearchDays() {
        return alternativeSearchDays;
    }

    public void setAlternativeSearchDays(int alternativeSearchDays) {
        this.alternativeSearchDays = alternativeSearchDays;
    }
}
