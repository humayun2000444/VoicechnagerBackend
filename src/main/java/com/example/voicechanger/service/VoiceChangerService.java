package com.example.voicechanger.service;

import com.example.voicechanger.dto.VoiceChangerDto;
import com.example.voicechanger.service.esl.EslService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class VoiceChangerService {

    private final EslService eslService;
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + "/voice_morph/";

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
    );

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private final Map<String, ScheduledTermination> scheduledTerminations = new ConcurrentHashMap<>();

    // Global call duration setting (in seconds)
    private volatile int globalCallDuration = 0; // 0 means disabled

    public VoiceChangerService(EslService eslService) {
        this.eslService = eslService;
    }

    // Voice type presets
    private static final Map<String, VoicePreset> VOICE_PRESETS = Map.of(
            "female", new VoicePreset(10, 2, 100),    // Male to Female
            "child", new VoicePreset(8, 4, 120),      // Child voice
            "monster", new VoicePreset(-15, -4, 300)  // Monster voice
    );

    // ------------------- Audio Processing Methods -------------------
    public File processAudioFile(MultipartFile audioFile, String voiceType) throws Exception {
        // Create temp directory if it doesn't exist
        Files.createDirectories(Path.of(TEMP_DIR));

        // Save uploaded file
        File inputFile = new File(TEMP_DIR + "input_" + System.currentTimeMillis() + ".wav");
        try (FileOutputStream fos = new FileOutputStream(inputFile)) {
            fos.write(audioFile.getBytes());
        }

        return processAudioFile(inputFile, voiceType);
    }

    public File processLiveAudio(byte[] audioData, String voiceType) throws Exception {
        // Create temp directory if it doesn't exist
        Files.createDirectories(Path.of(TEMP_DIR));

        // Save audio data to file
        File inputFile = new File(TEMP_DIR + "live_input_" + System.currentTimeMillis() + ".wav");
        try (FileOutputStream fos = new FileOutputStream(inputFile)) {
            fos.write(audioData);
        }

        return processAudioFile(inputFile, voiceType);
    }

    private File processAudioFile(File inputFile, String voiceType) throws Exception {
        // Get voice preset
        VoicePreset preset = VOICE_PRESETS.get(voiceType.toLowerCase());
        if (preset == null) {
            throw new IllegalArgumentException("Invalid voice type");
        }

        // Process audio using SoX (Sound eXchange) - you'll need to install sox
        File outputFile = new File(TEMP_DIR + "output_" + System.currentTimeMillis() + ".wav");

        String soxCommand = String.format("sox \"%s\" \"%s\" pitch %.1f formant %.1f echo 0.8 0.88 100 0.4",
                inputFile.getAbsolutePath(),
                outputFile.getAbsolutePath(),
                preset.shift,
                preset.formant);

        Process process = Runtime.getRuntime().exec(soxCommand);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Audio processing failed");
        }

        // Clean up input file
        inputFile.delete();

        return outputFile;
    }

    // Alternative method using FFmpeg (if you prefer)
    public File processAudioWithFFmpeg(MultipartFile audioFile, String voiceType) throws Exception {
        Files.createDirectories(Path.of(TEMP_DIR));

        File inputFile = new File(TEMP_DIR + "input_" + System.currentTimeMillis() + ".wav");
        try (FileOutputStream fos = new FileOutputStream(inputFile)) {
            fos.write(audioFile.getBytes());
        }

        VoicePreset preset = VOICE_PRESETS.get(voiceType.toLowerCase());
        if (preset == null) {
            throw new IllegalArgumentException("Invalid voice type");
        }

        File outputFile = new File(TEMP_DIR + "output_" + System.currentTimeMillis() + ".wav");

        // FFmpeg command for voice morphing
        String ffmpegCommand = String.format("ffmpeg -i \"%s\" -af \"asetrate=44100*%.2f,atempo=1/%.2f\" \"%s\"",
                inputFile.getAbsolutePath(),
                (1.0 + preset.shift/100.0),
                (1.0 + preset.shift/100.0),
                outputFile.getAbsolutePath());

        Process process = Runtime.getRuntime().exec(ffmpegCommand);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg processing failed");
        }

        inputFile.delete();
        return outputFile;
    }

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
            log.warn("⚠️ Invalid UUID format received: {}", request.uuid());
            return "Error: Invalid UUID format";
        }

        if (!eslService.isConnected()) {
            log.error("❌ ESL service not connected - cannot control voice changer");
            return "Error: FreeSWITCH connection not available";
        }

        String fsCommand;
        switch (request.command().toLowerCase()) {
            case "start":
                log.info("🎭 Starting voice changer for call {}", request.uuid());
                String directionCheck = eslService.sendCommand("uuid_dump " + request.uuid());
                if (!directionCheck.matches("(?s).*(Call-Direction: outbound|direction=outbound).*")) {
                    log.warn("⚠️ Voice changer start rejected - not an outbound call: {}", request.uuid());
                    return "Error: Voice changer can only be started on outbound calls";
                }
                fsCommand = "voicechanger start " + request.uuid();
                break;
            case "stop":
                log.info("🛑 Stopping voice changer for call {}", request.uuid());
                fsCommand = "voicechanger stop " + request.uuid();
                break;
            default:
                log.error("❌ Invalid voice changer command: {}", request.command());
                throw new IllegalArgumentException("Invalid command. Use 'start' or 'stop'");
        }

        String result = eslService.sendCommand(fsCommand);
        if (result.contains("-ERR") || result.startsWith("ERROR:")) {
            log.error("❌ Voice changer command failed: {}", result);
            return "Error: " + result;
        }
        log.info("✅ Voice changer command successful for call {}", request.uuid());
        return result;
    }

    // ------------------- Set Voice Type -------------------
    public String setVoiceType(VoiceChangerDto.VoiceTypeRequest request) {
        if (!isValidUuid(request.uuid())) {
            log.warn("⚠️ Invalid UUID format for voice type request: {}", request.uuid());
            return "Error: Invalid UUID format";
        }

        if (!eslService.isConnected()) {
            log.error("❌ ESL service not connected - cannot set voice type");
            return "Error: FreeSWITCH connection not available";
        }

        VoicePreset preset = VOICE_PRESETS.get(request.voiceType().toLowerCase());
        if (preset == null) {
            log.warn("⚠️ Invalid voice type requested: {}", request.voiceType());
            return "Error: Invalid voice type. Use 'female', 'child', or 'monster'";
        }

        log.info("🎤 Setting voice type '{}' for call {}", request.voiceType(), request.uuid());
        String fsCommand = String.format("voicechanger set %s %.1f %.1f %.1f",
                request.uuid(), preset.shift, preset.formant, preset.base);

        String result = eslService.sendCommand(fsCommand);
        if (result.contains("-ERR") || result.startsWith("ERROR:")) {
            log.error("❌ Voice type setting failed: {}", result);
            return "Error: " + result;
        }
        log.info("✅ Voice type '{}' applied successfully to call {}", request.voiceType(), request.uuid());
        return result;
    }

    // ------------------- Terminate Call -------------------
    public String terminateCall(VoiceChangerDto.TerminateRequest request) {
        if (!isValidUuid(request.uuid())) {
            log.warn("⚠️ Invalid UUID format for termination request: {}", request.uuid());
            return "Error: Invalid UUID format";
        }

        if (!eslService.isConnected()) {
            log.error("❌ ESL service not connected - cannot terminate call");
            return "Error: FreeSWITCH connection not available";
        }

        log.info("🔪 Terminating call {}", request.uuid());
        String directionCheck = eslService.sendCommand("uuid_dump " + request.uuid());
        if (!directionCheck.matches("(?s).*(Call-Direction: outbound|direction=outbound).*")) {
            log.warn("⚠️ Call termination rejected - not an outbound call: {}", request.uuid());
            return "Error: Only outbound calls can be terminated from this interface";
        }

        String result = eslService.sendCommand("uuid_kill " + request.uuid() + " NORMAL_CLEARING");
        if (result.contains("-ERR") || result.startsWith("ERROR:")) {
            log.error("❌ Call termination failed: {}", result);
            return "Error: " + result;
        }
        log.info("✅ Call {} terminated successfully", request.uuid());
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
            log.warn("⚠️ Invalid UUID format for status request: {}", uuid);
            throw new IllegalArgumentException("Invalid UUID format");
        }

        if (!eslService.isConnected()) {
            log.error("❌ ESL service not connected - cannot get voice changer status");
            return Map.of(
                    "active", false,
                    "status", "Error",
                    "message", "FreeSWITCH connection not available"
            );
        }

        log.debug("📊 Checking voice changer status for call {}", uuid);
        String statusOutput = eslService.sendCommand("voicechanger status " + uuid);
        boolean active = statusOutput.contains("+OK Running");

        log.debug("🎭 Voice changer status for {}: {}", uuid, active ? "Active" : "Inactive");
        return Map.of(
                "active", active,
                "status", active ? "Active" : "Inactive",
                "message", active ? "Voice magic is currently active" : "Voice magic is not active"
        );
    }

    // ------------------- Active Calls -------------------
    public List<VoiceChangerDto.CallInfo> getActiveCallUUIDs() {
        if (!eslService.isConnected()) {
            log.error("❌ ESL service not connected - cannot get active calls");
            return new ArrayList<>();
        }

        log.debug("📊 Retrieving active calls from FreeSWITCH");
        String result = eslService.sendCommand("show calls");
        List<VoiceChangerDto.CallInfo> calls = new ArrayList<>();

        if (result.startsWith("ERROR:")) {
            log.error("❌ Failed to get active calls: {}", result);
            return calls;
        }

        try {
            String[] lines = result.split("\n");
            boolean headerSkipped = false;

            for (String line : lines) {
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

            log.debug("📋 Found {} active calls", calls.size());
        } catch (Exception e) {
            log.error("❌ Error parsing active calls response: {}", e.getMessage(), e);
        }

        return calls;
    }

    // ------------------- Helpers -------------------
    private long getLiveCallDuration(String uuid) {
        try {
            String result = eslService.sendCommand("uuid_dump " + uuid);

            if (result.startsWith("ERROR:")) {
                log.debug("🔍 Could not get call duration for {}: {}", uuid, result);
                return 0;
            }

            String[] lines = result.split("\n");
            for (String line : lines) {
                if (line.startsWith("Caller-Channel-Answered-Time:")) {
                    try {
                        long answeredTime = Long.parseLong(line.split(": ")[1].trim());
                        if (answeredTime > 0) {
                            long duration = (System.currentTimeMillis() * 1000 - answeredTime) / 1_000_000;
                            log.trace("⏱️ Call {} duration: {} seconds", uuid, duration);
                            return duration;
                        }
                    } catch (NumberFormatException e) {
                        log.debug("📊 Could not parse answered time for call {}: {}", uuid, line);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("❌ Error computing live duration for call {}: {}", uuid, e.getMessage());
        }
        return 0;
    }

    private void terminateScheduledCall(String uuid) {
        try {
            log.info("⏰ Executing scheduled termination for call {}", uuid);
            String result = eslService.sendCommand("uuid_kill " + uuid + " NORMAL_CLEARING");
            scheduledTerminations.remove(uuid);

            if (result.contains("-ERR") || result.startsWith("ERROR:")) {
                log.error("❌ Scheduled termination failed for {}: {}", uuid, result);
            } else {
                log.info("✅ Scheduled termination executed successfully for {}", uuid);
            }
        } catch (Exception e) {
            log.error("❌ Error executing scheduled termination for {}: {}", uuid, e.getMessage(), e);
        }
    }

    private boolean cancelScheduledTermination(String uuid) {
        return scheduledTerminations.remove(uuid) != null;
    }

    private boolean isValidUuid(String uuid) {
        return uuid != null && UUID_PATTERN.matcher(uuid).matches();
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