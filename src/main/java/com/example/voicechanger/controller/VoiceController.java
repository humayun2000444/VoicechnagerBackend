package com.example.voicechanger.controller;


import com.example.voicechanger.dto.VoiceProcessRequest;
import com.example.voicechanger.service.VoiceProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/api")
public class VoiceController {

    private static final Logger logger = LoggerFactory.getLogger(VoiceController.class);

    @Autowired
    private VoiceProcessingService voiceProcessingService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/process")
    @ResponseBody
    public ResponseEntity<byte[]> processAudio(
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam(value = "shift", defaultValue = "10.0") double shift,
            @RequestParam(value = "formant", defaultValue = "2.0") double formant,
            @RequestParam(value = "base", defaultValue = "100.0") double base) {

        try {
            logger.info("Received /process request");
            logger.info("Shift: {}, Formant: {}, Base: {}", shift, formant, base);
            logger.info("Received audio file: name={}, size={} bytes, type={}",
                    audioFile.getOriginalFilename(), audioFile.getSize(), audioFile.getContentType());

            // Create request object
            VoiceProcessRequest request = new VoiceProcessRequest();
            request.setShift((float) shift);
            request.setFormant((float) formant);
            request.setBase((float) base);

            // Process audio
            byte[] processedAudio = voiceProcessingService.processAudio(audioFile.getBytes(), request);

            // Return processed audio
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "processed_audio.wav");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(processedAudio);

        } catch (Exception e) {
            logger.error("Error processing audio", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/process-live")
    @ResponseBody
    public ResponseEntity<byte[]> processLiveAudio(
            @RequestBody byte[] audioData,
            @RequestParam(value = "shift", defaultValue = "10.0") double shift,
            @RequestParam(value = "formant", defaultValue = "2.0") double formant,
            @RequestParam(value = "base", defaultValue = "100.0") double base) {

        try {
            logger.info("Received live audio processing request");
            logger.info("Shift: {}, Formant: {}, Base: {}", shift, formant, base);

            VoiceProcessRequest request = new VoiceProcessRequest();
            request.setShift((float) shift);
            request.setFormant((float) formant);
            request.setBase((float) base);

            byte[] processedAudio = voiceProcessingService.processAudio(audioData, request);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(processedAudio);

        } catch (Exception e) {
            logger.error("Error processing live audio", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}