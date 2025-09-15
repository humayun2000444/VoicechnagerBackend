package com.example.voicechnager.service;

import com.example.voicechnager.service.esl.CallTransferService;
import com.example.voicechnager.service.esl.EslService;
import org.springframework.stereotype.Service;

@Service
public class VoiceMorphService {
    private final CallTransferService callTransferService;
    private final EslService eslService;

    public VoiceMorphService(CallTransferService callTransferService, EslService eslService) {
        this.callTransferService = callTransferService;
        this.eslService = eslService;
    }


    public String setVoiceByEmail(String email, String code) {
        try {
            // Get channels in CSV format
            String channelsOutput = eslService.sendCommand("show channels as csv");
            if (channelsOutput == null || channelsOutput.isEmpty()) {
                return "No active channels found.";
            }

            // Parse CSV
            String[] lines = channelsOutput.split("\n");
            for (int i = 1; i < lines.length; i++) { // skip header
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                String[] cols = line.split(",", -1); // keep empty fields
                if (cols.length < 37) continue;

                String initialContext = cols[37]; // col 39 (0-based index 38)
                String cidName = cols[6];         // col 7  (0-based index 6)
                String bLegUuid = cols[0];        // col 1  (0-based index 0)

                if ("Voice".equalsIgnoreCase(initialContext) && cidName != null && !cidName.isEmpty()) {
                    String[] parts = cidName.split("_");
                    if (parts.length >= 3) {
                        String extractedEmail = parts[2]; // third part = email
                        if (extractedEmail.equalsIgnoreCase(email)) {
                            return executeVoiceCommand(bLegUuid, code);
                        }
                    }
                }
            }
            return "No active call found for email: " + email;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    private String executeVoiceCommand(String bLegUuid, String code) {
        try {
            String cmd;
            switch (code) {
                case "901": // female
                    callTransferService.setVoiceChangerParams(bLegUuid, "10", "2", "100");

                    break;
                case "902": // monster
                    callTransferService.setVoiceChangerParams(bLegUuid, "-15", "-4", "300");
                    break;
                case "903": // child
                    callTransferService.setVoiceChangerParams(bLegUuid, "8", "4", "120");
                    break;
                case "904": // stop
                    callTransferService.setVoiceChangerParams(bLegUuid, "1", "0", "0");
                    break;
                default:
                    return "Invalid code: " + code;
            }

            return "✅ Voice morph applied for B-leg UUID: " + bLegUuid + " (code " + code + ")";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error executing voice morph: " + e.getMessage();
        }
    }
}
