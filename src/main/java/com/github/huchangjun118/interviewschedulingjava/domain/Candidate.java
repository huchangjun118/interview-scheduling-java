package com.github.huchangjun118.interviewschedulingjava.domain;

/**
 * 候选人。
 */
public class Candidate {

    private String id;
    private String name;
    private Stage stage;

    public Candidate() {
    }

    public Candidate(String id, String name, Stage stage) {
        this.id = id;
        this.name = name;
        this.stage = stage;
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

    public Stage getStage() {
        return stage;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }
}
