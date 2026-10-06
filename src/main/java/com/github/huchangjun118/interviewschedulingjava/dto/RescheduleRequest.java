package com.github.huchangjun118.interviewschedulingjava.dto;

/**
 * 改期请求。
 */
public class RescheduleRequest {

    private TimeWindow newWindow;
    private String operatorId;
    private String operatorRole;
    private String reason;

    public TimeWindow getNewWindow() {
        return newWindow;
    }

    public void setNewWindow(TimeWindow newWindow) {
        this.newWindow = newWindow;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
