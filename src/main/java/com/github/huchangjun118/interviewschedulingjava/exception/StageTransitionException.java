package com.github.huchangjun118.interviewschedulingjava.exception;

import com.github.huchangjun118.interviewschedulingjava.domain.RecruitmentStage;

/**
 * 非法阶段流转。
 */
public class StageTransitionException extends RuntimeException {
    private final RecruitmentStage current;
    private final RecruitmentStage target;

    public StageTransitionException(RecruitmentStage current, RecruitmentStage target, String message) {
        super(message);
        this.current = current;
        this.target = target;
    }

    public RecruitmentStage getCurrent() {
        return current;
    }

    public RecruitmentStage getTarget() {
        return target;
    }
}
