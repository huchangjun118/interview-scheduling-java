package com.github.huchangjun118.interviewschedulingjava.domain;

/**
 * 候选人及其当前招聘阶段。
 */
public class Candidate {
    private String id;
    private String name;
    private RecruitmentStage stage = RecruitmentStage.APPLIED;

    public Candidate() {
    }

    public Candidate(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RecruitmentStage getStage() {
        return stage;
    }

    public void setStage(RecruitmentStage stage) {
        this.stage = stage;
    }
}
