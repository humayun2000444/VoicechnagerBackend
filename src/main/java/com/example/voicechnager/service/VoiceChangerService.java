//package com.example.voicechnager.service;
//
//import com.example.voicechnager.dto.VoiceChangerDto;
//import org.springframework.stereotype.Service;
//
//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//import java.util.*;
//import java.util.concurrent.*;
//import java.util.regex.Pattern;
//
//@Service
//public class VoiceChangerService {
//
//    private static final Pattern UUID_PATTERN = Pattern.compile(
//            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
//    );
//
//    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
//    private final Map<String, ScheduledTermination> scheduledTerminations = new ConcurrentHashMap<>();
//
//    // Voice type presets
//    private static final Map<String, VoicePreset> VOICE_PRESETS = Map.of(
//            "female", new VoicePreset(10, 2, 100),    // Male to Female
//            "child", new VoicePreset(8, 4, 120),      // Child voice
//            "monster", new VoicePreset(-15, -4, 300)  // Monster voice
//    );
//
//    // ------------------- Voice Changer Control -------------------
//    public String controlVoiceChanger(VoiceChangerDto.VoiceChangerRequest request) {
//        if (!isValidUuid(request.uuid())) {
//            return "Error: Invalid UUID format";
//        }
//
//        String fsCommand;
//        switch (request.command().toLowerCase()) {
//            case "start":
//                String directionCheck = executeFsCli("uuid_dump " + request.uuid() + " | grep -E 'Call-Direction|direction'");
//                if (!directionCheck.matches("(?s).*(Call-Direction: outbound|direction=outbound).*")) {
//                    return "Error: Voice changer can only be started on outbound calls";
//                }
//                fsCommand = "voicechanger start " + request.uuid();
//                break;
//            case "stop":
//                fsCommand = "voicechanger stop " + request.uuid();
//                break;
//            default:
//                throw new IllegalArgumentException("Invalid command. Use 'start' or 'stop'");
//        }
//
//        String result = executeFsCli(fsCommand);
//        if (result.contains("-ERR")) {
//            return "Error: " + result;
//        }
//        return result;
//    }
//
//    // ------------------- Set Voice Type -------------------
//    public String setVoiceType(VoiceChangerDto.VoiceTypeRequest request) {
//        if (!isValidUuid(request.uuid())) {
//            return "Error: Invalid UUID format";
//        }
//
//        VoicePreset preset = VOICE_PRESETS.get(request.voiceType().toLowerCase());
//        if (preset == null) {
//            return "Error: Invalid voice type. Use 'female', 'child', or 'monster'";
//        }
//
//        String fsCommand = String.format("voicechanger set %s %.1f %.1f %.1f",
//                request.uuid(), preset.shift, preset.formant, preset.base);
//
//        String result = executeFsCli(fsCommand);
//        if (result.contains("-ERR")) {
//            return "Error: " + result;
//        }
//        return result;
//    }
//
//    // ------------------- Terminate Call -------------------
//    public String terminateCall(VoiceChangerDto.TerminateRequest request) {
//        if (!isValidUuid(request.uuid())) {
//            return "Error: Invalid UUID format";
//        }
//
//        String directionCheck = executeFsCli("uuid_dump " + request.uuid() + " | grep -E 'Call-Direction|direction'");
//        if (!directionCheck.matches("(?s).*(Call-Direction: outbound|direction=outbound).*")) {
//            return "Error: Only outbound calls can be terminated from this interface";
//        }
//
//        String result = executeFsCli("uuid_kill " + request.uuid() + " NORMAL_CLEARING");
//        if (result.contains("-ERR")) {
//            return "Error: " + result;
//        }
//        return "Call terminated successfully";
//    }
//
//    // ------------------- Schedule Termination -------------------
//    public String scheduleTermination(VoiceChangerDto.ScheduleTerminationRequest request) {
//        if (!isValidUuid(request.uuid())) {
//            return "Error: Invalid UUID format";
//        }
//        if (request.seconds() <= 0) {
//            return "Error: Time must be a positive number";
//        }
//
//        cancelScheduledTermination(request.uuid());
//
//        long currentDurationSeconds = getLiveCallDuration(request.uuid());
//        long remainingSeconds = request.seconds() - currentDurationSeconds;
//
//        if (remainingSeconds <= 0) {
//            terminateScheduledCall(request.uuid());
//            return "Call already exceeded desired duration. Terminated immediately.";
//        }
//
//        ScheduledTermination scheduledTermination = new ScheduledTermination(
//                request.uuid(),
//                System.currentTimeMillis() + (remainingSeconds * 1000)
//        );
//
//        scheduler.schedule(() -> terminateScheduledCall(request.uuid()), remainingSeconds, TimeUnit.SECONDS);
//        scheduledTerminations.put(request.uuid(), scheduledTermination);
//
//        return String.format("Call scheduled to terminate in %d seconds", remainingSeconds);
//    }
//
//    public String cancelScheduledTermination(VoiceChangerDto.CancelScheduledRequest request) {
//        if (!isValidUuid(request.uuid())) {
//            return "Error: Invalid UUID format";
//        }
//        return cancelScheduledTermination(request.uuid()) ? "Scheduled termination cancelled" : "No scheduled termination found";
//    }
//
//    public List<VoiceChangerDto.ScheduleTerminationResponse> getScheduledTerminations() {
//        List<VoiceChangerDto.ScheduleTerminationResponse> responses = new ArrayList<>();
//        long currentTime = System.currentTimeMillis();
//
//        for (ScheduledTermination termination : scheduledTerminations.values()) {
//            long remainingSeconds = (termination.scheduledTime - currentTime) / 1000;
//            if (remainingSeconds > 0) {
//                responses.add(new VoiceChangerDto.ScheduleTerminationResponse(
//                        termination.uuid,
//                        remainingSeconds,
//                        termination.scheduledTime,
//                        getLiveCallDuration(termination.uuid),
//                        false,
//                        "Scheduled termination active"
//                ));
//            }
//        }
//        return responses;
//    }
//
//    // ------------------- Voice Changer Status -------------------
//    public Map<String, Object> getVoiceChangerStatus(String uuid) {
//        if (!isValidUuid(uuid)) {
//            throw new IllegalArgumentException("Invalid UUID format");
//        }
//
//        String statusOutput = executeFsCli("voicechanger status " + uuid);
//        boolean active = statusOutput.contains("+OK Running");
//
//        return Map.of(
//                "active", active,
//                "status", active ? "Active" : "Inactive",
//                "message", active ? "Voice magic is currently active" : "Voice magic is not active"
//        );
//    }
//
//    // ------------------- Active Calls -------------------
//    public List<VoiceChangerDto.CallInfo> getActiveCallUUIDs() {
//        String fsCommand = "show calls";
//        List<VoiceChangerDto.CallInfo> calls = new ArrayList<>();
//
//        try {
//            Process process = new ProcessBuilder("/usr/bin/fs_cli", "-x", fsCommand)
//                    .redirectErrorStream(true)
//                    .start();
//
//            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
//                boolean headerSkipped = false;
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    if (!headerSkipped) {
//                        headerSkipped = true;
//                        continue;
//                    }
//                    if (line.trim().isEmpty()) continue;
//
//                    String[] parts = line.split(",", -1);
//                    if (parts.length < 25) continue;
//
//                    String uuid = parts[21].trim();
//                    String caller = parts[5].trim();
//                    String callee = parts[6].trim();
//
//                    if (isValidUuid(uuid)) {
//                        long durationSeconds = getLiveCallDuration(uuid);
//                        calls.add(new VoiceChangerDto.CallInfo(uuid, caller, callee, durationSeconds));
//                    }
//                }
//            }
//            process.waitFor();
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to get active calls", e);
//        }
//
//        return calls;
//    }
//
//    // ------------------- Helpers -------------------
//    private long getLiveCallDuration(String uuid) {
//        try {
//            Process process = new ProcessBuilder("/usr/bin/fs_cli", "-x", "uuid_dump " + uuid)
//                    .redirectErrorStream(true)
//                    .start();
//
//            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
//                String line;
//                long answeredTime = 0;
//                while ((line = reader.readLine()) != null) {
//                    if (line.startsWith("Caller-Channel-Answered-Time:")) {
//                        answeredTime = Long.parseLong(line.split(": ")[1].trim());
//                        break;
//                    }
//                }
//                if (answeredTime > 0) {
//                    return (System.currentTimeMillis() * 1000 - answeredTime) / 1_000_000;
//                }
//            }
//            process.waitFor();
//        } catch (Exception e) {
//            System.err.println("Error computing live duration for UUID: " + uuid + ", " + e.getMessage());
//        }
//        return 0;
//    }
//
//    private void terminateScheduledCall(String uuid) {
//        try {
//            String result = executeFsCli("uuid_kill " + uuid + " NORMAL_CLEARING");
//            scheduledTerminations.remove(uuid);
//            System.out.println("Scheduled termination executed for UUID: " + uuid + ", Result: " + result);
//        } catch (Exception e) {
//            System.err.println("Error executing scheduled termination for UUID: " + uuid + ", Error: " + e.getMessage());
//        }
//    }
//
//    private boolean cancelScheduledTermination(String uuid) {
//        return scheduledTerminations.remove(uuid) != null;
//    }
//
//    private boolean isValidUuid(String uuid) {
//        return uuid != null && UUID_PATTERN.matcher(uuid).matches();
//    }
//
//    private String executeFsCli(String command) {
//        try {
//            Process process = new ProcessBuilder("/usr/bin/fs_cli", "-x", command)
//                    .redirectErrorStream(true)
//                    .start();
//
//            StringBuilder output = new StringBuilder();
//            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    output.append(line).append("\n");
//                }
//            }
//
//            int exitCode = process.waitFor();
//            if (exitCode != 0) {
//                return "Error (exit " + exitCode + "): " + output;
//            }
//            return output.toString().trim();
//        } catch (Exception e) {
//            return "Error executing command: " + e.getMessage();
//        }
//    }
//
//    // ------------------- Inner Classes -------------------
//    private static class ScheduledTermination {
//        private final String uuid;
//        private final long scheduledTime;
//
//        public ScheduledTermination(String uuid, long scheduledTime) {
//            this.uuid = uuid;
//            this.scheduledTime = scheduledTime;
//        }
//    }
//
//    private static class VoicePreset {
//        private final float shift;
//        private final float formant;
//        private final float base;
//
//        public VoicePreset(float shift, float formant, float base) {
//            this.shift = shift;
//            this.formant = formant;
//            this.base = base;
//        }
//    }
//}

package com.example.voicechnager.service;

import com.example.voicechnager.dto.VoiceChangerDto;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@Service
public class VoiceChangerService {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    );

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private final Map<String, ScheduledTermination> scheduledTerminations = new ConcurrentHashMap<>();

    // Global call duration setting (in seconds)
    private volatile int globalCallDuration = 0; // 0 means disabled

    // Voice type presets
    private static final Map<String, VoicePreset> VOICE_PRESETS = Map.of(
            "female", new VoicePreset(10, 2, 100),    // Male to Female
            "child", new VoicePreset(8, 4, 120),      // Child voice
            "monster", new VoicePreset(-15, -4, 300)  // Monster voice
    );

    // ------------------- Global Call Duration -------------------
    public Map<String, Object> setGlobalCallDuration(int seconds) {
        if (seconds < 0) {
            return Map.of("error", "Duration must be a positive number");
        }
        this.globalCallDuration = seconds;
        return Map.of(
                "message", String.format("Global call duration set to %d seconds. All new calls will be automatically terminated after this duration.", seconds),
                "duration", seconds,
                "status", "success"
        );
    }

    public synchronized int getGlobalCallDuration() {
        return this.globalCallDuration;
    }

    public Map<String, Object> clearGlobalCallDuration() {
        this.globalCallDuration = 0;
        return Map.of(
                "message", "Global call duration disabled",
                "duration", 0,
                "status", "success"
        );
    }

    // ------------------- Automatic Call Termination -------------------
    public void scheduleAutomaticTermination(String uuid) {
        if (globalCallDuration <= 0) {
            return; // Global duration not set
        }

        // Cancel any existing termination for this UUID
        cancelScheduledTermination(uuid);

        long currentDurationSeconds = getLiveCallDuration(uuid);
        long remainingSeconds = globalCallDuration - currentDurationSeconds;

        if (remainingSeconds <= 0) {
            // Call already exceeded global duration
            terminateScheduledCall(uuid);
            return;
        }

        ScheduledTermination scheduledTermination = new ScheduledTermination(
                uuid,
                System.currentTimeMillis() + (remainingSeconds * 1000)
        );

        scheduler.schedule(() -> terminateScheduledCall(uuid), remainingSeconds, TimeUnit.SECONDS);
        scheduledTerminations.put(uuid, scheduledTermination);

        System.out.println("Automatically scheduled termination for " + uuid + " in " + remainingSeconds + " seconds");
    }

    // ------------------- Voice Changer Control -------------------
    public String controlVoiceChanger(VoiceChangerDto.VoiceChangerRequest request) {
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

    // ------------------- Set Voice Type -------------------
    public String setVoiceType(VoiceChangerDto.VoiceTypeRequest request) {
        if (!isValidUuid(request.uuid())) {
            return "Error: Invalid UUID format";
        }

        VoicePreset preset = VOICE_PRESETS.get(request.voiceType().toLowerCase());
        if (preset == null) {
            return "Error: Invalid voice type. Use 'female', 'child', or 'monster'";
        }

        String fsCommand = String.format("voicechanger set %s %.1f %.1f %.1f",
                request.uuid(), preset.shift, preset.formant, preset.base);

        String result = executeFsCli(fsCommand);
        if (result.contains("-ERR")) {
            return "Error: " + result;
        }
        return result;
    }

    // ------------------- Terminate Call -------------------
    public String terminateCall(VoiceChangerDto.TerminateRequest request) {
        if (!isValidUuid(request.uuid())) {
            return "Error: Invalid UUID format";
        }

        String directionCheck = executeFsCli("uuid_dump " + request.uuid() + " | grep -E 'Call-Direction|direction'");
        if (!directionCheck.matches("(?s).*(Call-Direction: outbound|direction=outbound).*")) {
            return "Error: Only outbound calls can be terminated from this interface";
        }

        String result = executeFsCli("uuid_kill " + request.uuid() + " NORMAL_CLEARING");
        if (result.contains("-ERR")) {
            return "Error: " + result;
        }
        return "Call terminated successfully";
    }

    // ------------------- Schedule Termination -------------------
    public String scheduleTermination(VoiceChangerDto.ScheduleTerminationRequest request) {
        if (!isValidUuid(request.uuid())) {
            return "Error: Invalid UUID format";
        }
        if (request.seconds() <= 0) {
            return "Error: Time must be a positive number";
        }

        cancelScheduledTermination(request.uuid());

        long currentDurationSeconds = getLiveCallDuration(request.uuid());
        long remainingSeconds = request.seconds() - currentDurationSeconds;

        if (remainingSeconds <= 0) {
            terminateScheduledCall(request.uuid());
            return "Call already exceeded desired duration. Terminated immediately.";
        }

        ScheduledTermination scheduledTermination = new ScheduledTermination(
                request.uuid(),
                System.currentTimeMillis() + (remainingSeconds * 1000)
        );

        scheduler.schedule(() -> terminateScheduledCall(request.uuid()), remainingSeconds, TimeUnit.SECONDS);
        scheduledTerminations.put(request.uuid(), scheduledTermination);

        return String.format("Call scheduled to terminate in %d seconds", remainingSeconds);
    }

    public String cancelScheduledTermination(VoiceChangerDto.CancelScheduledRequest request) {
        if (!isValidUuid(request.uuid())) {
            return "Error: Invalid UUID format";
        }
        return cancelScheduledTermination(request.uuid()) ? "Scheduled termination cancelled" : "No scheduled termination found";
    }

    public List<VoiceChangerDto.ScheduleTerminationResponse> getScheduledTerminations() {
        List<VoiceChangerDto.ScheduleTerminationResponse> responses = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (ScheduledTermination termination : scheduledTerminations.values()) {
            long remainingSeconds = (termination.scheduledTime - currentTime) / 1000;
            if (remainingSeconds > 0) {
                responses.add(new VoiceChangerDto.ScheduleTerminationResponse(
                        termination.uuid,
                        remainingSeconds,
                        termination.scheduledTime,
                        getLiveCallDuration(termination.uuid),
                        false,
                        "Scheduled termination active"
                ));
            }
        }
        return responses;
    }

    // ------------------- Voice Changer Status -------------------
    public Map<String, Object> getVoiceChangerStatus(String uuid) {
        if (!isValidUuid(uuid)) {
            throw new IllegalArgumentException("Invalid UUID format");
        }

        String statusOutput = executeFsCli("voicechanger status " + uuid);
        boolean active = statusOutput.contains("+OK Running");

        return Map.of(
                "active", active,
                "status", active ? "Active" : "Inactive",
                "message", active ? "Voice magic is currently active" : "Voice magic is not active"
        );
    }

    // ------------------- Active Calls -------------------
    public List<VoiceChangerDto.CallInfo> getActiveCallUUIDs() {
        String fsCommand = "show calls";
        List<VoiceChangerDto.CallInfo> calls = new ArrayList<>();

        try {
            Process process = new ProcessBuilder("/usr/bin/fs_cli", "-x", fsCommand)
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                boolean headerSkipped = false;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!headerSkipped) {
                        headerSkipped = true;
                        continue;
                    }
                    if (line.trim().isEmpty()) continue;

                    String[] parts = line.split(",", -1);
                    if (parts.length < 25) continue;

                    String uuid = parts[21].trim();
                    String caller = parts[5].trim();
                    String callee = parts[6].trim();

                    if (isValidUuid(uuid)) {
                        long durationSeconds = getLiveCallDuration(uuid);
                        calls.add(new VoiceChangerDto.CallInfo(uuid, caller, callee, durationSeconds));

                        // Schedule automatic termination for new calls if global duration is set
                        if (globalCallDuration > 0 && !scheduledTerminations.containsKey(uuid)) {
                            scheduleAutomaticTermination(uuid);
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get active calls", e);
        }

        return calls;
    }

    // ------------------- Helpers -------------------
    private long getLiveCallDuration(String uuid) {
        try {
            Process process = new ProcessBuilder("/usr/bin/fs_cli", "-x", "uuid_dump " + uuid)
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                long answeredTime = 0;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Caller-Channel-Answered-Time:")) {
                        answeredTime = Long.parseLong(line.split(": ")[1].trim());
                        break;
                    }
                }
                if (answeredTime > 0) {
                    return (System.currentTimeMillis() * 1000 - answeredTime) / 1_000_000;
                }
            }
            process.waitFor();
        } catch (Exception e) {
            System.err.println("Error computing live duration for UUID: " + uuid + ", " + e.getMessage());
        }
        return 0;
    }

    private void terminateScheduledCall(String uuid) {
        try {
            String result = executeFsCli("uuid_kill " + uuid + " NORMAL_CLEARING");
            scheduledTerminations.remove(uuid);
            System.out.println("Scheduled termination executed for UUID: " + uuid + ", Result: " + result);
        } catch (Exception e) {
            System.err.println("Error executing scheduled termination for UUID: " + uuid + ", Error: " + e.getMessage());
        }
    }

    private boolean cancelScheduledTermination(String uuid) {
        return scheduledTerminations.remove(uuid) != null;
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
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
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

    // ------------------- Inner Classes -------------------
    private static class ScheduledTermination {
        private final String uuid;
        private final long scheduledTime;

        public ScheduledTermination(String uuid, long scheduledTime) {
            this.uuid = uuid;
            this.scheduledTime = scheduledTime;
        }
    }

    private static class VoicePreset {
        private final float shift;
        private final float formant;
        private final float base;

        public VoicePreset(float shift, float formant, float base) {
            this.shift = shift;
            this.formant = formant;
            this.base = base;
        }
    }
}