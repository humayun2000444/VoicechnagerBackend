//package com.example.voicechnager.controller;
//
//import com.example.voicechnager.dto.VoiceChangerDto;
//import com.example.voicechnager.dto.VoiceChangerDto.*;
//import com.example.voicechnager.service.VoiceChangerService;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.Map;
//
//@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
//@RestController
//@RequestMapping("/voicechanger")
//public class VoiceChangerController {
//
//    private final VoiceChangerService service;
//
//    public VoiceChangerController(VoiceChangerService service) {
//        this.service = service;
//    }
//
//    // Handle OPTIONS requests for CORS preflight
//    @RequestMapping(method = RequestMethod.OPTIONS, value = "/**")
//    public ResponseEntity<?> handleOptions() {
//        return ResponseEntity.ok().build();
//    }
//
//    @PostMapping
//    public String controlVoiceChanger(@RequestBody VoiceChangerDto.VoiceChangerRequest request) {
//        return service.controlVoiceChanger(request);
//    }
//
//    @PostMapping("/set-voice-type")
//    public ResponseEntity<String> setVoiceType(@RequestBody VoiceChangerDto.VoiceTypeRequest request) {
//        try {
//            String result = service.setVoiceType(request);
//            return ResponseEntity.ok(result);
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
//        }
//    }
//
//    @PostMapping("/terminate")
//    public String terminateCall(@RequestBody VoiceChangerDto.TerminateRequest request) {
//        return service.terminateCall(request);
//    }
//
//    @PostMapping("/schedule-termination")
//    public String scheduleTermination(@RequestBody VoiceChangerDto.ScheduleTerminationRequest request) {
//        return service.scheduleTermination(request);
//    }
//
//    @PostMapping("/cancel-schedule")
//    public String cancelScheduledTermination(@RequestBody CancelScheduledRequest request) {
//        return service.cancelScheduledTermination(request);
//    }
//
//    @GetMapping("/scheduled-terminations")
//    public List<ScheduleTerminationResponse> getScheduledTerminations() {
//        return service.getScheduledTerminations();
//    }
//
//    @GetMapping("/status")
//    public Map<String, Object> getVoiceChangerStatus(@RequestParam String uuid) {
//        return service.getVoiceChangerStatus(uuid);
//    }
//
//    @GetMapping("/calls")
//    public List<CallInfo> getActiveCallUUIDs() {
//        return service.getActiveCallUUIDs();
//    }
//}

package com.example.voicechnager.controller;

import com.example.voicechnager.dto.VoiceChangerDto;
import com.example.voicechnager.dto.VoiceChangerDto.*;
import com.example.voicechnager.service.VoiceChangerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
@RestController
@RequestMapping("/voicechanger")
public class VoiceChangerController {

    private final VoiceChangerService service;

    public VoiceChangerController(VoiceChangerService service) {
        this.service = service;
    }

    // Handle OPTIONS requests for CORS preflight
    @RequestMapping(method = RequestMethod.OPTIONS, value = "/**")
    public ResponseEntity<?> handleOptions() {
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public String controlVoiceChanger(@RequestBody VoiceChangerDto.VoiceChangerRequest request) {
        return service.controlVoiceChanger(request);
    }

    @PostMapping("/set-voice-type")
    public ResponseEntity<String> setVoiceType(@RequestBody VoiceChangerDto.VoiceTypeRequest request) {
        try {
            String result = service.setVoiceType(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/terminate")
    public String terminateCall(@RequestBody VoiceChangerDto.TerminateRequest request) {
        return service.terminateCall(request);
    }

    @PostMapping("/schedule-termination")
    public String scheduleTermination(@RequestBody VoiceChangerDto.ScheduleTerminationRequest request) {
        return service.scheduleTermination(request);
    }

    @PostMapping("/cancel-schedule")
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

    // ------------------- Global Call Duration -------------------
    @PostMapping("/global-duration")
    public ResponseEntity<Map<String, Object>> setGlobalCallDuration(@RequestBody VoiceChangerDto.GlobalDurationRequest request) {
        try {
            Map<String, Object> result = service.setGlobalCallDuration(request.seconds());
            return ResponseEntity.ok()
//                    .header("Access-Control-Allow-Origin", "*")
                    .body(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
//                    .header("Access-Control-Allow-Origin", "*")
                    .body(Map.of("error", "Error: " + e.getMessage()));
        }
    }

    @DeleteMapping("/global-duration")
    public ResponseEntity<Map<String, Object>> clearGlobalCallDuration() {
        try {
            Map<String, Object> result = service.clearGlobalCallDuration();
            return ResponseEntity.ok()
//                    .header("Access-Control-Allow-Origin", "*")
                    .body(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
//                    .header("Access-Control-Allow-Origin", "*")
                    .body(Map.of("error", "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/global-duration")
    public ResponseEntity<VoiceChangerDto.GlobalDurationResponse> getGlobalCallDuration() {
        try {
            int duration = service.getGlobalCallDuration();
            String status = duration > 0 ?
                    String.format("Active (%d seconds)", duration) : "Disabled";

            return ResponseEntity.ok(new VoiceChangerDto.GlobalDurationResponse(duration, status));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new VoiceChangerDto.GlobalDurationResponse(0, "Error"));
        }
    }
}