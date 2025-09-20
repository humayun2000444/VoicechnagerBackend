package com.example.voicechanger.service.esl;

import jakarta.annotation.PostConstruct;
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.transport.message.EslMessage;
import org.springframework.stereotype.Service;

@Service
public class EslService {

    private final FreeSwitchEventListener freeSwitchEventListener;
    private Client eslClient;

    private final String host = "127.0.0.1";
    private final int port = 8021;
    private final String password = "ClueCon";

    public EslService(FreeSwitchEventListener freeSwitchEventListener) {
        this.freeSwitchEventListener = freeSwitchEventListener;
    }

    @PostConstruct
    public void init() {
        eslClient = new Client();

        // Add the Spring-managed listener
        eslClient.addEventListener(freeSwitchEventListener);

        try {
            eslClient.connect(host, port, password, 10_000);
            eslClient.setEventSubscriptions("plain", "all");
            System.out.println("✅ Connected to FreeSWITCH ESL on " + host + ":" + port);
        } catch (org.freeswitch.esl.client.inbound.InboundConnectionFailure e) {
            System.err.println("❌ Failed to connect to FreeSWITCH ESL: " + e.getMessage());
        }
    }

    public String sendCommand(String command) {
        if (eslClient != null && eslClient.canSend()) {
            EslMessage response = eslClient.sendSyncApiCommand(command, "");
            if (response != null && response.getBodyLines() != null) {
                return String.join("\n", response.getBodyLines());
            }
        }
        return null;
    }

}
