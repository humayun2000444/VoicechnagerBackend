package com.example.voicechnager.service.esl;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TalkTimeService {

    private static final String AUTH_KEY = "59d09db@MGC40f434c36b2d36ed39e5d35be1@PXVI77be602ae166cb345549c3a0dfe";
    private static final String GET_API = "https://applb.magiccall.co:10016/mgcTest/talktime/getTalkTime";
    private static final String DEDUCT_API = "https://applb.magiccall.co:10016/mgcTest/talktime/deductTalkTime";

    private final RestTemplate restTemplate = new RestTemplate();
    private final EslService eslService;
    private final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    public TalkTimeService(EslService eslService) {
        this.eslService = eslService;
    }

    /**
     * Check & reserve talktime before bridging or transferring
     */
    public boolean checkAndReserveTalkTime(String uuid, String aParty, String bParty, String email) {
        try {
            String url = String.format("%s?aPartyMsisdn=%s&authKey=%s&bPartyMsisdn=%s&email=%s",
                    GET_API, aParty, AUTH_KEY, bParty, email);

            System.out.println("📡 GET TalkTime API request: " + url);
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            System.out.println("📥 GET TalkTime API response: " + body);

            if (body == null) return false;

            int statusCode = (int) body.get("statusCode");
            if (statusCode == 1001) {
                int talkTime = (int) body.get("talkTime");
                if (talkTime > 0) {
                    String sessionId = (String) body.get("sessionId");
                    Date startTime = new Date();

                    activeSessions.put(uuid, new SessionInfo(sessionId, startTime, talkTime));
                    System.out.printf("✅ TalkTime reserved: %ds for UUID=%s, sessionId=%s%n",
                            talkTime, uuid, sessionId);

                    // Schedule hangup after reserved talktime
                    new Thread(() -> {
                        try {
                            Thread.sleep(talkTime * 1000L);
                            if (activeSessions.containsKey(uuid)) {
                                System.out.println("⏰ TalkTime expired for UUID=" + uuid + " → killing call");
                                eslService.sendCommand("uuid_kill " + uuid);
                            }
                        } catch (InterruptedException ignored) {}
                    }).start();

                    return true;
                } else {
                    System.out.println("❌ TalkTime = 0, rejecting call UUID=" + uuid);
                    return false;
                }
            }

            System.out.println("❌ TalkTime check failed or insufficient → UUID=" + uuid);
            return false;

        } catch (Exception e) {
            System.err.println("❌ Error in checkAndReserveTalkTime: " + e.getMessage());
            return false;
        }
    }

    /**
     * Mark when the call is answered
     */
    public void markAnswered(String uuid) {
        SessionInfo session = activeSessions.get(uuid);
        if (session != null) {
            session.setAnswerTime(new Date());
            System.out.println("📞 Call answered → UUID=" + uuid + ", answerTime=" + session.getAnswerTime());
        }
    }

    /**
     * Deduct talktime after hangup
     */
    /**
     * Deduct talktime after hangup
     */
    public void deductTalkTime(String uuid, Date endTime) {
        try {
            SessionInfo session = activeSessions.remove(uuid);
            if (session == null) return;

            Date answerTime = session.getAnswerTime();
            if (answerTime == null) {
                System.out.println("⚠️ No answer time recorded, using startTime instead for UUID=" + uuid);
                answerTime = session.getStartTime();
            }

            // ✅ duration in whole seconds (no millis, no ceil)
            long diffSeconds = (endTime.getTime() - answerTime.getTime()) / 1000;
            int duration = (int) diffSeconds;

            String startStr = formatDate(answerTime);
            String endStr = formatDate(endTime);

            String url = String.format(
                    "%s?authKey=%s&callDuration=%d&callEndTime=%s&callStartTime=%s&sessionId=%s",
                    DEDUCT_API, AUTH_KEY, duration, endStr, startStr, session.getSessionId());

            System.out.println("📡 DeductTalkTime API request: " + url);
            String response = restTemplate.getForObject(url, String.class);
            System.out.println("📥 DeductTalkTime API response: " + response);
            System.out.println("💰 Deducted talk time for sessionId=" + session.getSessionId() +
                    ", Duration=" + duration + "s");

        } catch (Exception e) {
            System.err.println("❌ Error in deductTalkTime: " + e.getMessage());
        }
    }

    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

    private static class SessionInfo {
        private final String sessionId;
        private final Date startTime;   // when session reserved
        private Date answerTime;        // when call answered
        private final int talkTime;

        public SessionInfo(String sessionId, Date startTime, int talkTime) {
            this.sessionId = sessionId;
            this.startTime = startTime;
            this.talkTime = talkTime;
        }

        public String getSessionId() { return sessionId; }
        public Date getStartTime() { return startTime; }
        public Date getAnswerTime() { return answerTime; }
        public void setAnswerTime(Date answerTime) { this.answerTime = answerTime; }
        public int getTalkTime() { return talkTime; }
    }
}
