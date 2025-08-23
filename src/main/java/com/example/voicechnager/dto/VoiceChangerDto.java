package com.example.voicechnager.dto;

public class VoiceChangerDto {

    // ------------------- Requests -------------------
    public record VoiceChangerRequest(String command, String uuid) {}

    public record TerminateRequest(String uuid) {}

    public record ScheduleTerminationRequest(String uuid, int seconds) {}

    public record CancelScheduledRequest(String uuid) {}

    // ------------------- Responses -------------------
    public record CallInfo(String uuid, String caller, String callee, long durationSeconds) {}

    public record ScheduleTerminationResponse(
            String uuid,
            long remainingSeconds,
            long scheduledTime,
            long currentDurationSeconds,
            boolean terminatedImmediately,
            String message
    ) {}
}
