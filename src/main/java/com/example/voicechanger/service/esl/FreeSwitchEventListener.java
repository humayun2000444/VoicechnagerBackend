package com.example.voicechanger.service.esl;

import org.freeswitch.esl.client.IEslEventListener;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FreeSwitchEventListener implements IEslEventListener {

    private final CallHandlerService callHandlerService;

    public FreeSwitchEventListener(@Lazy CallHandlerService callHandlerService) {
        this.callHandlerService = callHandlerService;
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
            case "CHANNEL_BRIDGE" -> callHandlerService.handleBridge(headers);
            case "CHANNEL_PARK" -> callHandlerService.handlePark(headers);
            case "CHANNEL_ANSWER" -> callHandlerService.handleAnswer(headers);
            case "CHANNEL_HANGUP" -> callHandlerService.handleHangup(headers);
            case "CHANNEL_UNPARK" -> callHandlerService.handleUnpark(headers);
            default -> {
                // Ignore other events
            }
        }
    }
}
