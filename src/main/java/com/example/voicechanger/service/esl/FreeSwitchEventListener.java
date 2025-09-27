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
            log.debug("📨 [Event #{}] Received FreeSWITCH event: {}", eventId, event.getEventName());
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
            case "CHANNEL_BRIDGE" -> {
                log.info("🔗 [Event #{}] Processing CHANNEL_BRIDGE event", eventId);
                callHandlerService.handleBridge(headers);
            }
            case "CHANNEL_PARK" -> {
                log.info("📌 [Event #{}] Processing CHANNEL_PARK event", eventId);
                callHandlerService.handlePark(headers);
            }
            case "CHANNEL_ANSWER" -> {
                log.info("✅ [Event #{}] Processing CHANNEL_ANSWER event", eventId);
                callHandlerService.handleAnswer(headers);
            }
            case "CHANNEL_HANGUP" -> {
                log.info("❌ [Event #{}] Processing CHANNEL_HANGUP event", eventId);
                callHandlerService.handleHangup(headers);
            }
            case "CHANNEL_UNPARK" -> {
                log.info("📤 [Event #{}] Processing CHANNEL_UNPARK event", eventId);
                callHandlerService.handleUnpark(headers);
            }
            case "HEARTBEAT" -> {
                log.debug("💓 [Event #{}] FreeSWITCH heartbeat received - system healthy", eventId);
            }
            case "MODULE_LOAD", "MODULE_UNLOAD" -> {
                String moduleName = headers.getOrDefault("module", "unknown");
                log.info("🔧 [Event #{}] Module {} event: {}", eventId, eventName, moduleName);
            }
            case "SHUTDOWN" -> {
                log.warn("🛑 [Event #{}] FreeSWITCH shutdown detected!", eventId);
            }
            case "STARTUP" -> {
                log.info("🚀 [Event #{}] FreeSWITCH startup detected", eventId);
            }
            default -> {
                log.trace("📊 [Event #{}] Ignored event: {}", eventId, eventName);
            }
        }
    }

    public long getEventCount() {
        return eventCounter.get();
    }
}
