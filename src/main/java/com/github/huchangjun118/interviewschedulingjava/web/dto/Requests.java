package com.github.huchangjun118.interviewschedulingjava.web.dto;

import com.github.huchangjun118.interviewschedulingjava.domain.RecruitmentStage;
import com.github.huchangjun118.interviewschedulingjava.domain.Role;

import java.time.ZonedDateTime;

/**
 * REST 请求体。
 */
public final class Requests {

    private Requests() {
    }

    public record RegisterCandidateRequest(String candidateId, String name) {
    }

    public record AdvanceStageRequest(String operatorId, Role operatorRole, RecruitmentStage targetStage) {
    }

    public record AddAvailabilityRequest(String operatorId, Role operatorRole, String ownerId,
                                         ZonedDateTime start, ZonedDateTime end) {
    }

    public record RemoveAvailabilityRequest(String operatorId, Role operatorRole) {
    }

    public record ScheduleInterviewRequest(String requestKey, String operatorId, Role operatorRole,
                                           String candidateId, String interviewerId,
                                           RecruitmentStage stage,
                                           ZonedDateTime start, ZonedDateTime end) {
    }

    public record RescheduleInterviewRequest(String operatorId, Role operatorRole,
                                             ZonedDateTime start, ZonedDateTime end) {
    }

    public record CancelInterviewRequest(String operatorId, Role operatorRole, String reason) {
    }
}
