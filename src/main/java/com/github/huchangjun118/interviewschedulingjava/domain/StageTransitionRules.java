package com.github.huchangjun118.interviewschedulingjava.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 阶段流转规则：只允许沿既定顺序单步推进。
 */
public final class StageTransitionRules {

    private static final Map<Stage, Stage> NEXT = new EnumMap<>(Stage.class);

    static {
        NEXT.put(Stage.APPLIED, Stage.SCREENING);
        NEXT.put(Stage.SCREENING, Stage.INTERVIEWING);
        NEXT.put(Stage.INTERVIEWING, Stage.OFFER);
        NEXT.put(Stage.OFFER, Stage.HIRED);
    }

    private StageTransitionRules() {
    }

    /**
     * 判断 from -> to 是否为合法的单步推进。
     */
    public static boolean canAdvance(Stage from, Stage to) {
        return NEXT.get(from) == to;
    }

    /**
     * 返回当前阶段的下一个合法阶段，若已是终态则为空。
     */
    public static Optional<Stage> nextOf(Stage stage) {
        return Optional.ofNullable(NEXT.get(stage));
    }

    /**
     * 描述 from -> to 为何非法，用于拒绝时说明冲突原因。
     */
    public static String explainViolation(Stage from, Stage to) {
        if (canAdvance(from, to)) {
            return "合法流转";
        }
        Optional<Stage> next = nextOf(from);
        if (next.isEmpty()) {
            return "当前阶段 " + from + " 已是终态，不允许任何流转，目标阶段 " + to + " 被拒绝";
        }
        if (from == to) {
            return "当前阶段与目标阶段相同（" + from + "），不允许原地流转，唯一允许的下一阶段是 " + next.get();
        }
        if (to.ordinal() < from.ordinal()) {
            return "不允许回退：当前阶段 " + from + "，目标阶段 " + to + " 在其之前，唯一允许的下一阶段是 " + next.get();
        }
        return "不允许越级：当前阶段 " + from + " 只能推进到 " + next.get() + "，目标阶段 " + to + " 跳过了中间阶段";
    }
}
