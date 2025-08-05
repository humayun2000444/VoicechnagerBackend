package com.example.voicechnager.controller;

import org.springframework.web.bind.annotation.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/voicechanger")
public class VoiceChangerController {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    );

    @PostMapping
    public String controlVoiceChanger(@RequestBody VoiceChangerRequest request) {
        if (!isValidUuid(request.uuid())) {
            return "Error: Invalid UUID format";
        }

        String fsCommand;
        switch (request.command().toLowerCase()) {
            case "start":
                String directionCheck = executeFsCli("uuid_dump " + request.uuid() + " | grep -E 'Call-Direction|direction'");
                if (!directionCheck.matches("(?s).*(Call-Direction: outbound|direction=outbound).*")) {
                    return "Error: Voice changer can only be started on outbound calls";
                }
                fsCommand = "voicechanger start " + request.uuid();
                break;
            case "stop":
                fsCommand = "voicechanger stop " + request.uuid();
                break;
            default:
                throw new IllegalArgumentException("Invalid command. Use 'start' or 'stop'");
        }

        String result = executeFsCli(fsCommand);
        if (result.contains("-ERR")) {
            return "Error: " + result;
        }
        return result;
    }

    @PostMapping("/terminate")
    public String terminateCall(@RequestBody TerminateRequest request) {
        if (!isValidUuid(request.uuid())) {
            return "Error: Invalid UUID format";
        }

        // Optional: Check if call is outbound before allowing termination
        String directionCheck = executeFsCli("uuid_dump " + request.uuid() + " | grep -E 'Call-Direction|direction'");
        if (!directionCheck.matches("(?s).*(Call-Direction: outbound|direction=outbound).*")) {
            return "Error: Only outbound calls can be terminated from this interface";
        }

        String fsCommand = "uuid_kill " + request.uuid() + " NORMAL_CLEARING";
        String result = executeFsCli(fsCommand);

        if (result.contains("-ERR")) {
            return "Error: " + result;
        }
        return "Call terminated successfully";
    }

    @GetMapping("/status")
    public Map<String, Object> getVoiceChangerStatus(@RequestParam String uuid) {
        if (!isValidUuid(uuid)) {
            throw new IllegalArgumentException("Invalid UUID format");
        }

        // Use the voicechanger status command directly
        String statusOutput = executeFsCli("voicechanger status " + uuid);
        boolean active = statusOutput.contains("+OK Running");

        return Map.of(
                "active", active,
                "status", active ? "Active" : "Inactive",
                "message", active ? "Voice magic is currently active" : "Voice magic is not active"
        );
    }

    @GetMapping("/calls")
    public List<CallInfo> getActiveCallUUIDs() {
        String fsCommand = "show calls";
        List<CallInfo> calls = new ArrayList<>();

        try {
            Process process = new ProcessBuilder("/usr/bin/fs_cli", "-x", fsCommand)
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                boolean headerSkipped = false;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!headerSkipped) {
                        headerSkipped = true;
                        continue;
                    }
                    if (line.trim().isEmpty()) continue;

                    String[] parts = line.split(",", -1);
                    if (parts.length < 22) continue;

                    String uuid = parts[21].trim();
                    String caller = parts[5].trim();
                    String callee = parts[6].trim();

                    if (isValidUuid(uuid)) {
                        calls.add(new CallInfo(uuid, caller, callee));
                    }
                }
            }

            process.waitFor();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get active calls", e);
        }

        return calls;
    }

    private String extractPartnerUuid(String callInfo) {
        if (callInfo.contains("other_leg_unique_id:")) {
            return callInfo.replaceAll(".*other_leg_unique_id: ([^\\s]+).*", "$1").trim();
        }
        return null;
    }

    private boolean isValidUuid(String uuid) {
        return uuid != null && UUID_PATTERN.matcher(uuid).matches();
    }

    private String executeFsCli(String command) {
        try {
            Process process = new ProcessBuilder("/usr/bin/fs_cli", "-x", command)
                    .redirectErrorStream(true)
                    .start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return "Error (exit " + exitCode + "): " + output;
            }

            return output.toString().trim();
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }

    public record VoiceChangerRequest(String command, String uuid) {}
    public record TerminateRequest(String uuid) {}
    public record CallInfo(String uuid, String caller, String callee) {}
}