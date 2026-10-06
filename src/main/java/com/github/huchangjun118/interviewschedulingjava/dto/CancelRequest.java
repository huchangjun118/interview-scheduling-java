package com.github.huchangjun118.interviewschedulingjava.dto;

/**
 * 取消请求。
 */
public class CancelRequest {

    private String operatorId;
    private String operatorRole;
    private String reason;

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
