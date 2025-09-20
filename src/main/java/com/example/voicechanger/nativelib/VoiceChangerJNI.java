package com.example.voicechanger.nativelib;

public class VoiceChangerJNI {

    static {
        try {
            System.load(System.getProperty("user.dir") + "/src/main/resources/libvoicechanger.so");
            System.out.println("VoiceChanger native library loaded successfully!");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load native library: " + e.getMessage());
        }
    }

    public native long createProcessor(float sampleRate, int maxBufferLength);
    public native void destroyProcessor(long processorHandle);
    public native void setSettings(long processorHandle, float shift, float formant, float base);
    public native short[] processAudio(long processorHandle, short[] inputAudio, int length, float shift, float formant, float base);
    public native void reset(long processorHandle);
}
