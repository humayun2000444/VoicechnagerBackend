package com.example.voicechanger.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class VoiceProcessRequest {
    private float shift = 10.0f;      // Default: male-to-female
    private float formant = 2.0f;     // Default: male-to-female
    private float base = 100.0f;      // Default: male-to-female

    public VoiceProcessRequest() {}

    public VoiceProcessRequest(float shift, float formant, float base) {
        this.shift = shift;
        this.formant = formant;
        this.base = base;
    }

    @Override
    public String toString() {
        return "VoiceProcessRequest{" +
                "shift=" + shift +
                ", formant=" + formant +
                ", base=" + base +
                '}';
    }

    // Preset configurations
    public static VoiceProcessRequest maleToFemale() {
        return new VoiceProcessRequest(10.0f, 2.0f, 100.0f);
    }

    public static VoiceProcessRequest femaleToMale() {
        return new VoiceProcessRequest(-10.0f, -2.0f, 200.0f);
    }

    public static VoiceProcessRequest robotVoice() {
        return new VoiceProcessRequest(0.0f, 5.0f, 50.0f);
    }

    public static VoiceProcessRequest deepVoice() {
        return new VoiceProcessRequest(-15.0f, -3.0f, 250.0f);
    }

    public static VoiceProcessRequest highPitchVoice() {
        return new VoiceProcessRequest(15.0f, 3.0f, 80.0f);
    }
}