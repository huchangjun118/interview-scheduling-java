package com.github.huchangjun118.interviewschedulingjava.dto;

/**
 * 安排面试请求。
 */
public class ScheduleRequest {

    private String requestId;
    private String candidateId;
    private String interviewerId;
    private TimeWindow window;
    private String operatorId;
    private String operatorRole;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
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

    public TimeWindow getWindow() {
        return window;
    }

    public void setWindow(TimeWindow window) {
        this.window = window;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorRole() {
        return operatorRole;
    }

    public void setOperatorRole(String operatorRole) {
        this.operatorRole = operatorRole;
    }
}
