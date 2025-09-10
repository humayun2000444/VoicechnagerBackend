package com.example.voicechnager.service.esl;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TalkTimeService {

    private static final String AUTH_KEY = "541231";
    private static final String GET_API = "http://localhost:10016/mgcTest/talktime/getTalkTime";
    private static final String DEDUCT_API = "http://localhost:8080/talktime/deductTalkTime";

    private final RestTemplate restTemplate = new RestTemplate();
    private final EslService eslService;

    // Store active sessions: uuid -> session info
    private final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    public TalkTimeService(EslService eslService) {
        this.eslService = eslService;
    }

    /**
     * Request talk time before bridging
     */
    public boolean checkAndReserveTalkTime(String uuid, String aParty, String bParty, String email) {
        try {
            String url = String.format("%s?aPartyMsisdn=%s&authKey=%s&bPartyMsisdn=%s&email=%s",
                    GET_API, aParty, AUTH_KEY, bParty, email);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null) return false;

            int statusCode = (int) body.get("statusCode");
            if (statusCode == 1001) {
                int talkTime = (int) body.get("talkTime");
                if (talkTime > 0) {
                    String sessionId = (String) body.get("sessionId");
                    Date startTime = new Date();

                    activeSessions.put(uuid, new SessionInfo(sessionId, startTime, talkTime));

                    // Schedule hangup
                    new Thread(() -> {
                        try {
                            Thread.sleep(talkTime * 1000L);
                            if (activeSessions.containsKey(uuid)) {
                                System.out.println("⏰ TalkTime expired for UUID=" + uuid + " → hanging up");
                                eslService.sendCommand("uuid_kill " + uuid);
                            }
                        } catch (InterruptedException ignored) {}
                    }).start();

                    return true;
                }
            }

            // If failed or talkTime=0 → drop call immediately
            eslService.sendCommand("uuid_kill " + uuid);
            return false;

        } catch (Exception e) {
            System.err.println("❌ Error in checkAndReserveTalkTime: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deduct balance after hangup
     */
    public void deductTalkTime(String uuid, int duration, Date endTime) {
        try {
            SessionInfo session = activeSessions.remove(uuid);
            if (session == null) return;

            String startStr = formatDate(session.getStartTime());
            String endStr = formatDate(endTime);

            String url = String.format("%s?authKey=%s&callDuration=%d&callEndTime=%s&callStartTime=%s&sessionId=%s",
                    DEDUCT_API, AUTH_KEY, duration, endStr, startStr, session.getSessionId());

            restTemplate.getForObject(url, String.class);
            System.out.println("💰 Deducted talk time for sessionId=" + session.getSessionId());

        } catch (Exception e) {
            System.err.println("❌ Error in deductTalkTime: " + e.getMessage());
        }
    }

    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

    private static class SessionInfo {
        private final String sessionId;
        private final Date startTime;
        private final int talkTime;

        public SessionInfo(String sessionId, Date startTime, int talkTime) {
            this.sessionId = sessionId;
            this.startTime = startTime;
            this.talkTime = talkTime;
        }

        public String getSessionId() { return sessionId; }
        public Date getStartTime() { return startTime; }
        public int getTalkTime() { return talkTime; }
    }
}
