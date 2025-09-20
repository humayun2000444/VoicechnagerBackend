package com.example.voicechanger.service.esl;

import org.springframework.stereotype.Service;

@Service
public class CallTransferService {

    private final EslService eslService;

    public CallTransferService(EslService eslService) {
        this.eslService = eslService;
    }

    /**
     * Transfer parked call to default context
     */
    public void transferToDefault(String uuid, String calledNumber) {
        try {
            String command = String.format("uuid_transfer %s %s XML Voice", uuid, calledNumber);
            String response = eslService.sendCommand(command);
            System.out.println("➡️ Executed transfer: " + command);
            System.out.println("✅ Response: " + response);
        } catch (Exception e) {
            System.err.println("❌ Failed to transfer call: " + e.getMessage());
        }
    }

    /**
     * Start voicechanger
     */
    public void startVoiceChanger(String uuid) {
        try {
            String command = String.format("voicechanger start %s", uuid);
            String response = eslService.sendCommand(command);
            System.out.println("🎙️ Voicechanger started for UUID=" + uuid);
            System.out.println("✅ Response: " + response);
        } catch (Exception e) {
            System.err.println("❌ Failed to start voicechanger: " + e.getMessage());
        }
    }

    /**
     * Set voicechanger parameters
     */
    public void setVoiceChangerParams(String uuid, String... params) {
        try {
            String args = String.join(" ", params);
            String command = String.format("voicechanger set %s %s", uuid, args);
            String response = eslService.sendCommand(command);
            System.out.println("🎛️ Voicechanger params set for UUID=" + uuid + " [" + args + "]");
            System.out.println("✅ Response: " + response);
        } catch (Exception e) {
            System.err.println("❌ Failed to set voicechanger params: " + e.getMessage());
        }
    }
}
