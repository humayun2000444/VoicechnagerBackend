package com.example.voicechanger.service.esl;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.transport.message.EslMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class EslService {

    private final FreeSwitchEventListener freeSwitchEventListener;
    private volatile Client eslClient;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    @Value("${freeswitch.esl.host:127.0.0.1}")
    private String host;

    @Value("${freeswitch.esl.port:8021}")
    private int port;

    @Value("${freeswitch.esl.password:ClueCon}")
    private String password;

    @Value("${freeswitch.esl.timeout:10000}")
    private int connectionTimeout;

    @Value("${freeswitch.esl.reconnect.interval:5}")
    private int reconnectInterval;

    @Value("${freeswitch.esl.reconnect.max.attempts:10}")
    private int maxReconnectAttempts;

    public EslService(FreeSwitchEventListener freeSwitchEventListener) {
        this.freeSwitchEventListener = freeSwitchEventListener;
    }

    @PostConstruct
    public void init() {
        log.info("🚀 Initializing ESL Service - connecting to FreeSWITCH at {}:{}", host, port);
        connect();
        startConnectionMonitor();
    }

    @PreDestroy
    public void shutdown() {
        log.info("🛑 Shutting down ESL Service");
        reconnectScheduler.shutdown();
        if (eslClient != null) {
            try {
                eslClient.close();
                log.info("✅ ESL connection closed gracefully");
            } catch (Exception e) {
                log.warn("⚠️ Error closing ESL connection: {}", e.getMessage());
            }
        }
    }

    private void connect() {
        try {
            if (eslClient != null) {
                try {
                    eslClient.close();
                } catch (Exception e) {
                    log.debug("Error closing existing client: {}", e.getMessage());
                }
            }

            eslClient = new Client();
            eslClient.addEventListener(freeSwitchEventListener);

            log.info("🔌 Attempting to connect to FreeSWITCH ESL at {}:{}", host, port);
            eslClient.connect(host, port, password, connectionTimeout);
            eslClient.setEventSubscriptions("plain", "all");

            isConnected.set(true);
            reconnectAttempts.set(0);
            reconnecting.set(false);

            log.info("✅ Successfully connected to FreeSWITCH ESL on {}:{}", host, port);
            log.info("📡 ESL event subscription activated - monitoring all FreeSWITCH events");

        } catch (Exception e) {
            isConnected.set(false);
            log.error("❌ Failed to connect to FreeSWITCH ESL: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!reconnecting.compareAndSet(false, true)) {
            return; // Already reconnecting
        }

        int attempts = reconnectAttempts.incrementAndGet();

        if (attempts > maxReconnectAttempts) {
            log.error("💥 Maximum reconnection attempts ({}) exceeded. Giving up reconnection.", maxReconnectAttempts);
            reconnecting.set(false);
            return;
        }

        long delay = Math.min(reconnectInterval * attempts, 60); // Max 60 seconds delay

        log.warn("🔄 Scheduling reconnection attempt #{} in {} seconds", attempts, delay);

        reconnectScheduler.schedule(() -> {
            log.info("🔄 Reconnection attempt #{} starting...", attempts);
            connect();
        }, delay, TimeUnit.SECONDS);
    }

    private void startConnectionMonitor() {
        reconnectScheduler.scheduleAtFixedRate(() -> {
            if (!isConnected() && !reconnecting.get()) {
                log.warn("💔 Connection lost detected by monitor - initiating reconnection");
                scheduleReconnect();
            }
        }, 30, 30, TimeUnit.SECONDS); // Check every 30 seconds
    }

    public String sendCommand(String command) {
        if (!isConnected()) {
            log.warn("⚠️ Cannot send command '{}' - ESL not connected", command);
            return "ERROR: ESL not connected";
        }

        try {
            log.debug("📤 Sending ESL command: {}", command);
            EslMessage response = eslClient.sendSyncApiCommand(command, "");

            if (response != null && response.getBodyLines() != null) {
                String result = String.join("\n", response.getBodyLines());
                log.debug("📥 ESL response: {}", result);
                return result;
            } else {
                log.warn("⚠️ Empty response for command: {}", command);
                return "ERROR: Empty response";
            }
        } catch (Exception e) {
            log.error("❌ Error sending ESL command '{}': {}", command, e.getMessage());
            isConnected.set(false); // Mark as disconnected to trigger reconnection
            return "ERROR: " + e.getMessage();
        }
    }

    public boolean isConnected() {
        return isConnected.get() && eslClient != null && eslClient.canSend();
    }

    public String getConnectionStatus() {
        if (isConnected()) {
            return String.format("✅ Connected to %s:%d", host, port);
        } else if (reconnecting.get()) {
            return String.format("🔄 Reconnecting to %s:%d (attempt %d/%d)",
                    host, port, reconnectAttempts.get(), maxReconnectAttempts);
        } else {
            return String.format("❌ Disconnected from %s:%d", host, port);
        }
    }

    public int getReconnectAttempts() {
        return reconnectAttempts.get();
    }

    public void forceReconnect() {
        log.info("🔄 Manual reconnection requested");
        isConnected.set(false);
        reconnectAttempts.set(0);
        reconnecting.set(false);
        connect();
    }
}
