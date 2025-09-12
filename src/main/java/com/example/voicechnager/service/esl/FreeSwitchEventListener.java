package com.example.voicechnager.service.esl;

import org.freeswitch.esl.client.IEslEventListener;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

@Component
public class FreeSwitchEventListener implements IEslEventListener {

    private final CallTransferService callTransferService;
    private final TalkTimeService talkTimeService;
    private final EslService eslService;

    public FreeSwitchEventListener(@Lazy CallTransferService callTransferService,
                                   @Lazy TalkTimeService talkTimeService,
                                   @Lazy EslService eslService) {
        this.callTransferService = callTransferService;
        this.talkTimeService = talkTimeService;
        this.eslService = eslService;
    }

    @Override
    public void eventReceived(EslEvent event) {
        handleEvent(event);
    }

    @Override
    public void backgroundJobResultReceived(EslEvent event) {
        System.out.println("Background job result: " + event.getEventName());
    }

    private void handleEvent(EslEvent event) {
        String eventName = event.getEventName();
        Map<String, String> headers = event.getEventHeaders();

        switch (eventName) {

            case "CHANNEL_BRIDGE":
                handleChannelBridge(headers);
                break;

            case "CHANNEL_PARK":
                handleChannelPark(headers);
                break;

            case "CHANNEL_ANSWER":
                System.out.println("✅ Call answered: " +
                        headers.get("Caller-Caller-ID-Number") +
                        " (UUID=" + headers.get("Unique-ID") + ")");
                break;

            case "CHANNEL_HANGUP":
                handleChannelHangup(headers);
                break;

            case "CHANNEL_UNPARK":
                System.out.println("📤 Call unparked: " +
                        headers.get("Caller-Caller-ID-Number") +
                        " (UUID=" + headers.get("Unique-ID") + ")");
                break;

            default:
                // ignore other events
        }
    }

    private void handleChannelBridge(Map<String, String> headers) {
        String aLegUuid = headers.get("Bridge-A-Unique-ID");
        String bLegUuid = headers.get("Bridge-B-Unique-ID");
        String userName = headers.get("variable_user_name");

        System.out.println("\n🔗 CHANNEL_BRIDGE event");
        System.out.println("   A-leg UUID = " + aLegUuid);
        System.out.println("   B-leg UUID = " + bLegUuid);
        System.out.println("   variable_user_name = " + userName);

        if (bLegUuid != null && userName != null) {
            String[] parts = userName.split("_");
            if (parts.length >= 3) {
                String aParty = parts[0];
                String bParty = parts[1];
                String email = parts[2];

                System.out.printf("🔍 Checking TalkTime for aParty=%s, bParty=%s, email=%s%n",
                        aParty, bParty, email);

                boolean allowed = talkTimeService.checkAndReserveTalkTime(bLegUuid, aParty, bParty, email);
                if (!allowed) {
                    System.out.println("❌ Call rejected due to insufficient talktime (UUID=" + bLegUuid + ")");
                    eslService.sendCommand("uuid_kill " + bLegUuid);
                    return;
                }
            } else {
                System.out.println("⚠️ Invalid userName format: " + userName);
            }

            // ✅ Apply voicechanger rules
            if (userName.endsWith("_901")) {
                callTransferService.startVoiceChanger(bLegUuid);
            } else if (userName.endsWith("_902")) {
                callTransferService.startVoiceChanger(bLegUuid);
                callTransferService.setVoiceChangerParams(bLegUuid, "-15", "-4", "300");
            } else if (userName.endsWith("_903")) {
                callTransferService.startVoiceChanger(bLegUuid);
                callTransferService.setVoiceChangerParams(bLegUuid, "8", "4", "120");
            } else if (userName.endsWith("_904")) {
                System.out.println("➡️ Normal call bridge, no voicechanger applied.");
            }
        }
    }

    private void handleChannelPark(Map<String, String> headers) {
        String uuid = headers.get("Unique-ID");
        String calledNumber = headers.get("Caller-Destination-Number");
        String userName = headers.get("variable_user_name");

        System.out.println("📌 Call parked: " +
                headers.get("Caller-Caller-ID-Number") +
                " (UUID=" + uuid + ", Dest=" + calledNumber + ")");

        if (uuid != null && calledNumber != null && userName != null) {
            String[] parts = userName.split("_");
            if (parts.length >= 3) {
                String aParty = parts[0];
                String bParty = parts[1];
                String email = parts[2];

                // Pre-check talktime **before transferring**
                boolean allowed = talkTimeService.checkAndReserveTalkTime(uuid, aParty, bParty, email);
                if (!allowed) {
                    System.out.println("❌ Call dropped from park due to insufficient talktime (UUID=" + uuid + ")");
                    eslService.sendCommand("uuid_kill " + uuid);
                    return;
                }

                // Transfer to default after successful talktime check
                callTransferService.transferToDefault(uuid, calledNumber);
            } else {
                System.out.println("⚠️ Invalid userName format: " + userName);
            }
        }
    }

    private void handleChannelHangup(Map<String, String> headers) {
        String hangupUuid = headers.get("Unique-ID");
        System.out.println("\n❌ CHANNEL_HANGUP event");
        System.out.println("   Caller = " + headers.get("Caller-Caller-ID-Number"));
        System.out.println("   UUID = " + hangupUuid);

        talkTimeService.deductTalkTime(hangupUuid, new Date());
    }
}
