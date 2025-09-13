package com.example.voicechnager.service.esl;

import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

@Service
public class CallHandlerService {

    private final TalkTimeService talkTimeService;
    private final CallTransferService callTransferService;
    private final EslService eslService;

    public CallHandlerService(TalkTimeService talkTimeService,
                              CallTransferService callTransferService,
                              EslService eslService) {
        this.talkTimeService = talkTimeService;
        this.callTransferService = callTransferService;
        this.eslService = eslService;
    }

    public void handleBridge(Map<String, String> headers) {
        String aLegUuid = headers.get("Bridge-A-Unique-ID");
        String bLegUuid = headers.get("Bridge-B-Unique-ID");

        String userName = headers.getOrDefault("variable_user_name", headers.get("Caller-ANI"));

        System.out.printf("🔗 CHANNEL_BRIDGE event | A=%s, B=%s, user=%s%n", aLegUuid, bLegUuid, userName);

        if (bLegUuid == null || userName == null) return;

        String[] parts = userName.split("_");
        if (parts.length < 3) {
            System.out.println("⚠️ Invalid userName format: " + userName);
            return;
        }

        String aParty = parts[0], bParty = parts[1], email = parts[2];

        boolean allowed = talkTimeService.checkAndReserveTalkTime(bLegUuid, aParty, bParty, email);
        if (!allowed) {
            System.out.println("❌ Call rejected due to insufficient talktime (UUID=" + bLegUuid + ")");
            eslService.sendCommand("uuid_kill " + bLegUuid);
            return;
        }

        // Apply voicechanger rules
        applyVoiceChanger(bLegUuid, userName);
    }

    public void handlePark(Map<String, String> headers) {
        String uuid = headers.get("Unique-ID");
        String calledNumber = headers.get("Caller-Destination-Number");
        String userName = headers.getOrDefault("variable_user_name", headers.get("Caller-ANI"));

        System.out.printf("📌 Call parked | UUID=%s, Dest=%s, user=%s%n", uuid, calledNumber, userName);

        if (uuid == null || calledNumber == null || userName == null) return;

        String[] parts = userName.split("_");
        if (parts.length < 3) {
            System.out.println("⚠️ Invalid userName format: " + userName);
            return;
        }

        String aParty = parts[0], bParty = parts[1], email = parts[2];

        if (!talkTimeService.checkAndReserveTalkTime(uuid, aParty, bParty, email)) {
            System.out.println("❌ Call dropped from park due to insufficient talktime (UUID=" + uuid + ")");
            eslService.sendCommand("uuid_kill " + uuid);
            return;
        }

        callTransferService.transferToDefault(uuid, calledNumber);
    }

    public void handleAnswer(Map<String, String> headers) {
        String uuid = headers.get("Unique-ID");
        String caller = headers.get("Caller-Caller-ID-Number");
        System.out.println("✅ Call answered: " + caller + " (UUID=" + uuid + ")");
        talkTimeService.markAnswered(uuid);
    }

    public void handleHangup(Map<String, String> headers) {
        String hangupUuid = headers.get("Unique-ID");
        String caller = headers.get("Caller-Caller-ID-Number");
        String direction = headers.get("Call-Direction");

        System.out.printf("❌ CHANNEL_HANGUP | Caller=%s, UUID=%s, Direction=%s%n", caller, hangupUuid, direction);

        if ("inbound".equalsIgnoreCase(direction)) {
            talkTimeService.deductTalkTime(hangupUuid, new Date());
        }
    }

    public void handleUnpark(Map<String, String> headers) {
        String uuid = headers.get("Unique-ID");
        String caller = headers.get("Caller-Caller-ID-Number");
        System.out.printf("📤 Call unparked | Caller=%s, UUID=%s%n", caller, uuid);
    }

    private void applyVoiceChanger(String uuid, String userName) {
        switch (userName.substring(userName.lastIndexOf("_") + 1)) {
            case "901" -> callTransferService.startVoiceChanger(uuid);
            case "902" -> {
                callTransferService.startVoiceChanger(uuid);
                callTransferService.setVoiceChangerParams(uuid, "-15", "-4", "300");
            }
            case "903" -> {
                callTransferService.startVoiceChanger(uuid);
                callTransferService.setVoiceChangerParams(uuid, "8", "4", "120");
            }
            case "904" -> System.out.println("➡️ Normal call bridge, no voicechanger applied.");
        }
    }
}
