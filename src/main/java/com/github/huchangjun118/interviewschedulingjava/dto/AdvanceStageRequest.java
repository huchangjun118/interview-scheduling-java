package com.github.huchangjun118.interviewschedulingjava.dto;

/**
 * 阶段推进请求。
 */
public class AdvanceStageRequest {

    private String targetStage;
    private String operatorId;
    private String operatorRole;

    public String getTargetStage() {
        return targetStage;
    }

    public void setTargetStage(String targetStage) {
        this.targetStage = targetStage;
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
