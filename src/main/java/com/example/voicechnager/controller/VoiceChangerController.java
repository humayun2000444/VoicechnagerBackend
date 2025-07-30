package com.example.voicechnager.controller;

import org.springframework.web.bind.annotation.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/voicechanger")
public class VoiceChangerController {

    @GetMapping
    public String controlVoiceChanger(@RequestParam String command,
                                      @RequestParam String uuid,
                                      @RequestParam(required = false) String value) {
        String fsCommand;

        switch (command.toLowerCase()) {
            case "start":
                fsCommand = "voicechanger start " + uuid;
                break;
            case "set":
                if (value == null || value.isEmpty()) {
                    return "Error: 'value' is required for set command";
                }
                fsCommand = "voicechanger set " + uuid + " " + value;
                break;
            case "stop":
                fsCommand = "voicechanger stop " + uuid;
                break;
            default:
                return "Error: Invalid command. Use start, set, or stop.";
        }

        return executeFsCli(fsCommand);
    }

    // New endpoint to get all active call UUIDs (returns JSON array)
    @GetMapping("/calls")
    public List<String> getActiveCallUUIDs() {
        String fsCommand = "show calls";
        List<String> uuids = new ArrayList<>();

        try {
            ProcessBuilder pb = new ProcessBuilder("/usr/local/freeswitch/bin/fs_cli", "-x", fsCommand);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                if (!headerSkipped) {
                    // The first line is header, skip it
                    headerSkipped = true;
                    continue;
                }
                if (line.trim().isEmpty()) continue;

                // Each line is CSV; first column is uuid, 19th column is second UUID (b_uuid)
                // We want to collect both the 1st UUID and the 19th (b_uuid) if present

                String[] parts = line.split(",");
                if (parts.length < 19) continue;

                String uuid1 = parts[21].trim();
//                String uuid2 = parts[21].trim();

                if (!uuid1.isEmpty() && !uuids.contains(uuid1)) {
                    uuids.add(uuid1);
                }
//                if (!uuid2.isEmpty() && !uuids.contains(uuid2)) {
//                    uuids.add(uuid2);
//                }
            }

            process.waitFor();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return uuids;
    }

    private String executeFsCli(String fsCommand) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/usr/local/freeswitch/bin/fs_cli", "-x", fsCommand);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                return "fs_cli exited with code " + exitCode;
            }

            return result.toString();
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }
}
