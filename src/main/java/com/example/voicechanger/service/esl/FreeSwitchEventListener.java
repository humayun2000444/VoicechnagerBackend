package com.example.voicechanger.service.esl;

import lombok.extern.slf4j.Slf4j;
import org.freeswitch.esl.client.IEslEventListener;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class FreeSwitchEventListener implements IEslEventListener {

    private final CallHandlerService callHandlerService;
    private final AtomicLong eventCounter = new AtomicLong(0);

    public FreeSwitchEventListener(@Lazy CallHandlerService callHandlerService) {
        this.callHandlerService = callHandlerService;
    }

    @Override
    public void eventReceived(EslEvent event) {
        long eventId = eventCounter.incrementAndGet();

        try {
            handleEvent(event, eventId);
        } catch (Exception e) {
            log.error("❌ [Event #{}] Error processing event {}: {}", eventId, event.getEventName(), e.getMessage(), e);
        }
    }

    @Override
    public void backgroundJobResultReceived(EslEvent event) {
        long eventId = eventCounter.incrementAndGet();
        log.info("🔄 [Event #{}] Background job result: {} - {}", eventId, event.getEventName(),
                event.getEventHeaders().getOrDefault("Job-UUID", "unknown"));
    }

    private void handleEvent(EslEvent event, long eventId) {
        String eventName = event.getEventName();
        Map<String, String> headers = event.getEventHeaders();

        switch (eventName) {
            case "CHANNEL_BRIDGE" -> callHandlerService.handleBridge(headers);
            case "CHANNEL_PARK" -> callHandlerService.handlePark(headers);
            case "CHANNEL_ANSWER" -> callHandlerService.handleAnswer(headers);
            case "CHANNEL_HANGUP" -> callHandlerService.handleHangup(headers);
            case "CHANNEL_UNPARK" -> callHandlerService.handleUnpark(headers);
            case "HEARTBEAT" -> {
                // Silent heartbeat - system healthy
            }
            case "MODULE_LOAD", "MODULE_UNLOAD" -> {
                String moduleName = headers.getOrDefault("module", "unknown");
                log.info("🔧 Module {} event: {}", eventName, moduleName);
            }
            case "SHUTDOWN" -> {
                log.warn("🛑 FreeSWITCH shutdown detected!");
            }
            case "STARTUP" -> {
                log.info("🚀 FreeSWITCH startup detected");
            }
            default -> {
                // Silent - ignore other events
            }
        }
    }

    public long getEventCount() {
        return eventCounter.get();
    }
}
