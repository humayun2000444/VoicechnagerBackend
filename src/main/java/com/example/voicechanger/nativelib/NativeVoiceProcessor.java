
package com.example.voicechanger.nativelib;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class NativeVoiceProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NativeVoiceProcessor.class);
    // Utility methods
    @Getter
    private static boolean libraryLoaded = false;

    static {
        loadNativeLibrary();
    }

    private static void loadNativeLibrary() {
        if (libraryLoaded) return;

        try {
            // ALWAYS load from the specific path: /src/main/resources/native/libvoicechanger.so
            String absolutePath = System.getProperty("user.dir") + "/src/main/resources/native/libvoicechanger.so";
            logger.info("Loading native library from: {}", absolutePath);

            // Verify the file exists
            File libraryFile = new File(absolutePath);
            if (!libraryFile.exists()) {
                throw new RuntimeException("Native library not found at: " + absolutePath);
            }

            System.load(absolutePath);
            logger.info("✓ Successfully loaded native library from: {}", absolutePath);
            libraryLoaded = true;

        } catch (UnsatisfiedLinkError e) {
            logger.error("Failed to load native library from explicit path", e);
            throw new RuntimeException("Could not load native voice processing library from: " +
                    System.getProperty("user.dir") + "/src/main/resources/native/libvoicechanger.so", e);
        } catch (Exception e) {
            logger.error("Unexpected error loading native library", e);
            throw new RuntimeException("Unexpected error loading native library", e);
        }
    }

    // Native method declarations
    public native long createProcessor();
    public native void destroyProcessor(long processorHandle);
    public native boolean configureProcessor(long processorHandle, int sampleRate, int maxBufferLength);
    public native boolean setSettings(long processorHandle, float shiftSemitones, float formantSemitones, float formantBaseHz);
    public native short[] processAudioNative(long processorHandle, short[] inputBuffer, int length);

    // Java wrapper methods
    private long processorHandle = 0;

    public NativeVoiceProcessor() {
        if (!libraryLoaded) {
            throw new RuntimeException("Native library not loaded");
        }
        processorHandle = createProcessor();
        if (processorHandle == 0) {
            throw new RuntimeException("Failed to create native processor");
        }
        logger.info("Native processor created successfully with handle: {}", processorHandle);
    }

    public boolean configure(int sampleRate, int maxBufferLength) {
        if (processorHandle == 0) return false;
        return configureProcessor(processorHandle, sampleRate, maxBufferLength);
    }

    public boolean configure(int sampleRate) {
        return configure(sampleRate, 8192);
    }

    public boolean updateSettings(float shift, float formant, float base) {
        if (processorHandle == 0) return false;
        return setSettings(processorHandle, shift, formant, base);
    }

    public short[] processAudio(short[] inputBuffer, int length, float shift, float formant, float base, int sampleRate) {
        if (processorHandle == 0) {
            logger.error("Processor not initialized");
            return inputBuffer; // return original if processing fails
        }

        try {
            // Configure if needed
            configure(sampleRate, Math.max(length, 8192));

            // Update settings
            updateSettings(shift, formant, base);

            // Process audio
            short[] result = processAudioNative(processorHandle, inputBuffer, length);

            if (result == null) {
                logger.warn("Native processing returned null, returning original audio");
                return inputBuffer;
            }

            return result;

        } catch (Exception e) {
            logger.error("Error during native audio processing", e);
            return inputBuffer; // return original on error
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (processorHandle != 0) {
            destroyProcessor(processorHandle);
            processorHandle = 0;
        }
        super.finalize();
    }

    public void dispose() {
        if (processorHandle != 0) {
            destroyProcessor(processorHandle);
            processorHandle = 0;
        }
    }

    public boolean isProcessorValid() {
        return processorHandle != 0;
    }
}