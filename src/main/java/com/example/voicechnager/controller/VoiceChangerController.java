package com.example.voicechnager.controller;

import com.example.voicechnager.dto.VoiceChangerDto;
import com.example.voicechnager.dto.VoiceChangerDto.*;
import com.example.voicechnager.service.VoiceChangerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/voicechanger")
public class VoiceChangerController {

    private final VoiceChangerService service;

    public VoiceChangerController(VoiceChangerService service) {
        this.service = service;
    }

    @PostMapping
    public String controlVoiceChanger(@RequestBody VoiceChangerDto.VoiceChangerRequest request) {
        return service.controlVoiceChanger(request);
    }

    @PostMapping("/terminate")
    public String terminateCall(@RequestBody VoiceChangerDto.TerminateRequest request) {
        return service.terminateCall(request);
    }

    @PostMapping("/schedule-termination")
    public String scheduleTermination(@RequestBody VoiceChangerDto.ScheduleTerminationRequest request) {
        return service.scheduleTermination(request);
    }

    @PostMapping("/cancel-scheduled-termination")
    public String cancelScheduledTermination(@RequestBody CancelScheduledRequest request) {
        return service.cancelScheduledTermination(request);
    }

    @GetMapping("/scheduled-terminations")
    public List<ScheduleTerminationResponse> getScheduledTerminations() {
        return service.getScheduledTerminations();
    }

    @GetMapping("/status")
    public Map<String, Object> getVoiceChangerStatus(@RequestParam String uuid) {
        return service.getVoiceChangerStatus(uuid);
    }

    @GetMapping("/calls")
    public List<CallInfo> getActiveCallUUIDs() {
        return service.getActiveCallUUIDs();
    }
}
