package com.example.voicechnager.service.esl;

import org.freeswitch.esl.client.IEslEventListener;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FreeSwitchEventListener implements IEslEventListener {

    private final CallTransferService callTransferService;

    public FreeSwitchEventListener(@Lazy CallTransferService callTransferService) {
        this.callTransferService = callTransferService;
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
                String aLegUuid = headers.get("Bridge-A-Unique-ID");
                String bLegUuid = headers.get("Bridge-B-Unique-ID");
                String userName = headers.get("variable_user_name");

                System.out.println("🔗 Call bridged!");
                System.out.println("   A-leg UUID = " + aLegUuid);
                System.out.println("   B-leg UUID = " + bLegUuid);
                System.out.println("   variable_user_name = " + userName);

                if (bLegUuid != null && userName != null) {
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
                    } else {
                        System.out.println("ℹ️ No matching rule for userName=" + userName);
                    }
                }
                break;

            case "CHANNEL_PARK":
                String uuid = headers.get("Unique-ID");
                String calledNumber = headers.get("Caller-Destination-Number");

                System.out.println("📌 Call parked: " +
                        headers.get("Caller-Caller-ID-Number") +
                        " (UUID=" + uuid + ", Dest=" + calledNumber + ")");

                if (uuid != null && calledNumber != null) {
                    callTransferService.transferToDefault(uuid, calledNumber);
                }
                break;

            case "CHANNEL_ANSWER":
                System.out.println("✅ Call answered: " +
                        headers.get("Caller-Caller-ID-Number") +
                        " (UUID=" + headers.get("Unique-ID") + ")");
                break;

            case "CHANNEL_HANGUP":
                System.out.println("❌ Call hangup: " +
                        headers.get("Caller-Caller-ID-Number") +
                        " (UUID=" + headers.get("Unique-ID") + ")");
                break;

            case "CHANNEL_UNPARK":
                System.out.println("📤 Call unparked: " +
                        headers.get("Caller-Caller-ID-Number") +
                        " (UUID=" + headers.get("Unique-ID") + ")");
                break;

            default:
                // System.out.println("⚡ Event: " + eventName);
        }
    }
}
