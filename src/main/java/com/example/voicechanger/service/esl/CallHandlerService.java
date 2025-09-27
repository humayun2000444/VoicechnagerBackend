package com.example.voicechanger.service.esl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

@Slf4j
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

        log.info("🔗 CHANNEL_BRIDGE event | A-Leg={}, B-Leg={}, User={}", aLegUuid, bLegUuid, userName);

        if (bLegUuid == null || userName == null) {
            log.warn("⚠️ Missing required bridge parameters - A-Leg={}, B-Leg={}, User={}", aLegUuid, bLegUuid, userName);
            return;
        }

        try {
            applyVoiceChanger(bLegUuid, userName);
        } catch (Exception e) {
            log.error("❌ Error applying voice changer for bridge {}: {}", bLegUuid, e.getMessage(), e);
        }
    }

    public void handlePark(Map<String, String> headers) {
        String uuid = headers.get("Unique-ID");
        String calledNumber = headers.get("Caller-Destination-Number");
        String userName = headers.getOrDefault("variable_user_name", headers.get("Caller-ANI"));

        log.info("📌 Call parked | UUID={}, Destination={}, User={}", uuid, calledNumber, userName);

        if (uuid == null || calledNumber == null || userName == null) {
            log.warn("⚠️ Missing required park parameters - UUID={}, Destination={}, User={}", uuid, calledNumber, userName);
            return;
        }

        String[] parts = userName.split("_");
        if (parts.length < 3) {
            log.warn("⚠️ Invalid userName format: {} (expected format: aParty_bParty_email)", userName);
            return;
        }

        String aParty = parts[0], bParty = parts[1], email = parts[2];
        log.debug("📋 Parsed user data - A-Party={}, B-Party={}, Email={}", aParty, bParty, email);

        try {
            if (!talkTimeService.checkAndReserveTalkTime(uuid, aParty, bParty, email)) {
                log.warn("❌ Call {} dropped from park due to insufficient talk time", uuid);
                eslService.sendCommand("uuid_kill " + uuid);
                return;
            }

            callTransferService.transferToDefault(uuid, calledNumber);
            log.info("✅ Call {} successfully transferred after park validation", uuid);
        } catch (Exception e) {
            log.error("❌ Error handling park for call {}: {}", uuid, e.getMessage(), e);
        }
    }

    public void handleAnswer(Map<String, String> headers) {
        String uuid = headers.get("Unique-ID");
        String caller = headers.get("Caller-Caller-ID-Number");

        log.info("✅ Call answered | Caller={}, UUID={}", caller, uuid);

        try {
            talkTimeService.markAnswered(uuid);
            log.debug("📝 Call {} marked as answered in talk time service", uuid);
        } catch (Exception e) {
            log.error("❌ Error marking call {} as answered: {}", uuid, e.getMessage(), e);
        }
    }

    public void handleHangup(Map<String, String> headers) {
        String hangupUuid = headers.get("Unique-ID");
        String caller = headers.get("Caller-Caller-ID-Number");
        String direction = headers.get("Call-Direction");
        String hangupCause = headers.get("Hangup-Cause");

        log.info("❌ Call hangup | Caller={}, UUID={}, Direction={}, Cause={}", caller, hangupUuid, direction, hangupCause);

        try {
            if ("inbound".equalsIgnoreCase(direction)) {
                talkTimeService.deductTalkTime(hangupUuid, new Date());
                log.debug("💰 Talk time deducted for inbound call {}", hangupUuid);
            } else {
                log.debug("📞 Outbound call {} ended - no talk time deduction", hangupUuid);
            }
        } catch (Exception e) {
            log.error("❌ Error processing hangup for call {}: {}", hangupUuid, e.getMessage(), e);
        }
    }

    public void handleUnpark(Map<String, String> headers) {
        String uuid = headers.get("Unique-ID");
        String caller = headers.get("Caller-Caller-ID-Number");

        log.info("📤 Call unparked | Caller={}, UUID={}", caller, uuid);
        log.debug("🚀 Call {} is now active and being processed", uuid);
    }

    private void applyVoiceChanger(String uuid, String userName) {
        String suffix = userName.substring(userName.lastIndexOf("_") + 1);

        switch (suffix) {
            case "901" -> {
                log.info("🎭 Applying standard voice changer for call {}", uuid);
                callTransferService.startVoiceChanger(uuid);
            }
            case "902" -> {
                log.info("👹 Applying monster voice preset for call {}", uuid);
                callTransferService.startVoiceChanger(uuid);
                callTransferService.setVoiceChangerParams(uuid, "-15", "-4", "300");
            }
            case "903" -> {
                log.info("👶 Applying child voice preset for call {}", uuid);
                callTransferService.startVoiceChanger(uuid);
                callTransferService.setVoiceChangerParams(uuid, "8", "4", "120");
            }
            case "904" -> {
                log.info("📞 Normal call bridge for {} - no voice changer applied", uuid);
            }
            default -> {
                log.warn("⚠️ Unknown voice changer suffix '{}' for user {} - defaulting to normal call", suffix, userName);
            }
        }
    }
}
