package com.github.huchangjun118.interviewschedulingjava.domain;

/**
 * 招聘阶段，按既定顺序推进。
 */
public enum RecruitmentStage {
    APPLIED,
    SCREENING,
    PHONE_INTERVIEW,
    TECHNICAL_INTERVIEW,
    ONSITE_INTERVIEW,
    OFFER,
    HIRED;

    /**
     * 校验从当前阶段到目标阶段的流转是否合法。
     *
     * @param target 目标阶段
     * @return 非法时返回冲突说明，合法时返回 null
     */
    public String transitionError(RecruitmentStage target) {
        if (target == null) {
            return "目标阶段不能为空";
        }
        if (target == this) {
            return "当前已处于阶段 " + this + "，无需重复流转";
        }
        int diff = target.ordinal() - this.ordinal();
        if (diff < 0) {
            return "不允许回退：当前阶段 " + this + " 已晚于目标阶段 " + target;
        }
        if (diff > 1) {
            return "不允许越级：当前阶段 " + this + " 只能推进到 " + next().orElse(this)
                    + "，无法直接跳转到 " + target;
        }
        if (this == HIRED) {
            return "当前阶段 " + this + " 已是终态，无法继续流转到 " + target;
        }
        return null;
    }

    public java.util.Optional<RecruitmentStage> next() {
        int nextOrdinal = this.ordinal() + 1;
        if (nextOrdinal >= values().length) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(values()[nextOrdinal]);
    }
}
