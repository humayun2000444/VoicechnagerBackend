package com.example.voicechanger.config;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class ServerInfoLogger {

    @EventListener(ApplicationReadyEvent.class)
    public void logServerInfo() {
        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            String port = System.getProperty("server.port", "8080");

            System.out.println("✅ Voice Changer Backend is running at: http://" + ip + ":" + port);
        } catch (UnknownHostException e) {
            System.err.println("Failed to determine server IP: " + e.getMessage());
        }
    }
}
