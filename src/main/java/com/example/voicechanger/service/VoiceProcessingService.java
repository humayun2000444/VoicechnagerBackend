package com.example.voicechanger.service;

import com.example.voicechanger.dto.VoiceProcessRequest;
import com.example.voicechanger.nativelib.NativeVoiceProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Service
public class VoiceProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(VoiceProcessingService.class);
    private static final int SAMPLE_RATE = 44100;
    private static final int SAMPLE_SIZE = 16; // 16-bit
    private static final int CHANNELS = 1; // mono

    private final NativeVoiceProcessor nativeProcessor;

    public VoiceProcessingService() {
        this.nativeProcessor = new NativeVoiceProcessor();
    }

    public byte[] processAudio(byte[] audioData, VoiceProcessRequest request) throws IOException {
        logger.info("Processing audio: {} bytes", audioData.length);

        try {
            // Convert audio to PCM if needed
            short[] pcmData = convertToPCM16(audioData);
            logger.info("Converted to PCM: {} samples", pcmData.length);

            // Process with native library
            short[] processedPcm = nativeProcessor.processAudio(
                    pcmData,
                    pcmData.length,
                    request.getShift(),
                    request.getFormant(),
                    request.getBase(),
                    SAMPLE_RATE
            );

            logger.info("Native processing complete: {} samples", processedPcm.length);

            // Convert back to byte array and create WAV
            return createWavFile(processedPcm);

        } catch (Exception e) {
            logger.error("Error in audio processing", e);
            throw new IOException("Failed to process audio: " + e.getMessage(), e);
        }
    }

    private short[] convertToPCM16(byte[] audioData) throws IOException {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bais);

            AudioFormat sourceFormat = audioInputStream.getFormat();
            logger.info("Source format: {} Hz, {} channels, {} bits",
                    sourceFormat.getSampleRate(), sourceFormat.getChannels(), sourceFormat.getSampleSizeInBits());

            // Define target format (16-bit PCM, mono, 44.1kHz)
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    SAMPLE_RATE,
                    SAMPLE_SIZE,
                    CHANNELS,
                    CHANNELS * (SAMPLE_SIZE / 8), // frame size
                    SAMPLE_RATE, // frame rate
                    false // little endian
            );

            // Convert if necessary
            if (!AudioSystem.isConversionSupported(targetFormat, sourceFormat)) {
                throw new IOException("Conversion from " + sourceFormat + " to " + targetFormat + " not supported");
            }

            AudioInputStream convertedStream = AudioSystem.getAudioInputStream(targetFormat, audioInputStream);

            // Read all data
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = convertedStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }

            byte[] pcmBytes = baos.toByteArray();

            // Convert to short array
            short[] samples = new short[pcmBytes.length / 2];
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);

            convertedStream.close();
            audioInputStream.close();

            return samples;

        } catch (UnsupportedAudioFileException e) {
            logger.error("Unsupported audio format", e);
            throw new IOException("Unsupported audio format. Please upload WAV or MP3.", e);
        }
    }

    private byte[] createWavFile(short[] pcmData) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // WAV header
        int sampleRate = SAMPLE_RATE;
        int bitsPerSample = SAMPLE_SIZE;
        int channels = CHANNELS;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcmData.length * 2; // 2 bytes per sample
        int fileSize = 36 + dataSize;

        // RIFF header
        dos.writeBytes("RIFF");
        dos.writeInt(Integer.reverseBytes(fileSize));
        dos.writeBytes("WAVE");

        // Format chunk
        dos.writeBytes("fmt ");
        dos.writeInt(Integer.reverseBytes(16)); // chunk size
        dos.writeShort(Short.reverseBytes((short) 1)); // PCM format
        dos.writeShort(Short.reverseBytes((short) channels));
        dos.writeInt(Integer.reverseBytes(sampleRate));
        dos.writeInt(Integer.reverseBytes(byteRate));
        dos.writeShort(Short.reverseBytes((short) blockAlign));
        dos.writeShort(Short.reverseBytes((short) bitsPerSample));

        // Data chunk
        dos.writeBytes("data");
        dos.writeInt(Integer.reverseBytes(dataSize));

        // PCM data
        for (short sample : pcmData) {
            dos.writeShort(Short.reverseBytes(sample));
        }

        dos.close();
        return baos.toByteArray();
    }
}