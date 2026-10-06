package com.github.huchangjun118.interviewschedulingjava.domain;

/**
 * 面试官（招聘官）。
 */
public class Interviewer {

    private String id;
    private String name;

    public Interviewer() {
    }

    public Interviewer(String id, String name) {
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
}
