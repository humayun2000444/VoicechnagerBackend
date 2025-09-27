package com.example.voicechanger.controller;

import com.example.voicechanger.service.esl.EslService;
import com.example.voicechanger.service.esl.FreeSwitchEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/esl")
public class EslMonitorController {

    private final EslService eslService;
    private final FreeSwitchEventListener eventListener;

    public EslMonitorController(EslService eslService, FreeSwitchEventListener eventListener) {
        this.eslService = eslService;
        this.eventListener = eventListener;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getEslStatus() {
        log.info("📊 ESL status check requested");

        return ResponseEntity.ok(Map.of(
                "connected", eslService.isConnected(),
                "status", eslService.getConnectionStatus(),
                "reconnectAttempts", eslService.getReconnectAttempts(),
                "eventsProcessed", eventListener.getEventCount(),
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        ));
    }

    @PostMapping("/reconnect")
    public ResponseEntity<Map<String, Object>> forceReconnect() {
        log.info("🔄 Manual ESL reconnection requested via API");

        try {
            eslService.forceReconnect();
            return ResponseEntity.ok(Map.of(
                    "message", "Reconnection initiated successfully",
                    "status", "success",
                    "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ));
        } catch (Exception e) {
            log.error("❌ Error during manual reconnection: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Error during reconnection: " + e.getMessage(),
                    "status", "error",
                    "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ));
        }
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection() {
        log.info("🧪 ESL connection test requested");

        try {
            String result = eslService.sendCommand("status");
            boolean isWorking = result != null && !result.startsWith("ERROR");

            return ResponseEntity.ok(Map.of(
                    "connected", eslService.isConnected(),
                    "testResult", isWorking ? "SUCCESS" : "FAILED",
                    "response", result,
                    "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ));
        } catch (Exception e) {
            log.error("❌ Error during connection test: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "connected", false,
                    "testResult", "FAILED",
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        boolean isHealthy = eslService.isConnected();
        String status = isHealthy ? "UP" : "DOWN";

        Map<String, Object> healthData = Map.of(
                "status", status,
                "eslConnection", eslService.getConnectionStatus(),
                "eventsProcessed", eventListener.getEventCount(),
                "reconnectAttempts", eslService.getReconnectAttempts()
        );

        return isHealthy ?
                ResponseEntity.ok(healthData) :
                ResponseEntity.status(503).body(healthData);
    }
}