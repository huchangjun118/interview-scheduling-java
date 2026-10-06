package com.github.huchangjun118.interviewschedulingjava.domain;

import java.time.Instant;

/**
 * 一场已确认的面试安排。
 */
public class Interview {
    private String id;
    /** 幂等键：同一安排请求重复提交时返回同一场面试 */
    private String requestKey;
    private String candidateId;
    private String interviewerId;
    private RecruitmentStage stage;
    private TimeWindow window;
    private InterviewStatus status = InterviewStatus.SCHEDULED;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Interview() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(String requestKey) {
        this.requestKey = requestKey;
    }

    public String getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(String candidateId) {
        this.candidateId = candidateId;
    }

    public String getInterviewerId() {
        return interviewerId;
    }

    public void setInterviewerId(String interviewerId) {
        this.interviewerId = interviewerId;
    }

    public RecruitmentStage getStage() {
        return stage;
    }

    public void setStage(RecruitmentStage stage) {
        this.stage = stage;
    }

    public TimeWindow getWindow() {
        return window;
    }

    public void setWindow(TimeWindow window) {
        this.window = window;
    }

    public InterviewStatus getStatus() {
        return status;
    }

    public void setStatus(InterviewStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
