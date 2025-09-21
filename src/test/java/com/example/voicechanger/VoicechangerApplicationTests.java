package com.example.voicechnager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "voice.changer.max.buffer.size=8192",
        "voice.changer.sample.rate=44100"
})
class VoicechangerApplicationTests {

    @Test
    void contextLoads() {
        // Test that the Spring Boot application context loads successfully
    }

    @Test
    void applicationStarts() {
        // This test will pass if the application can start without errors
        // The @SpringBootTest annotation will attempt to load the full application context
    }
}