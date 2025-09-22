package com.example.voicechanger.service;

import com.example.voicechanger.dto.VoiceProcessRequest;
import com.example.voicechanger.nativelib.NativeVoiceProcessor;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.probe.FFmpegStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Service
public class VoiceProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(VoiceProcessingService.class);
    private static final int SAMPLE_RATE = 8000;
    private static final int SAMPLE_SIZE = 16; // 16-bit
    private static final int CHANNELS = 1; // mono

    private final NativeVoiceProcessor nativeProcessor;
    private final FFmpeg ffmpeg;
    private final FFprobe ffprobe;

    public VoiceProcessingService() {
        this.nativeProcessor = new NativeVoiceProcessor();
        try {
            // Initialize FFmpeg (assumes ffmpeg is in PATH)
            this.ffmpeg = new FFmpeg("ffmpeg");
            this.ffprobe = new FFprobe("ffprobe");
        } catch (IOException e) {
            logger.warn("FFmpeg not found in PATH. MP3 conversion will not be available.", e);
            throw new RuntimeException("FFmpeg initialization failed", e);
        }
    }

    public byte[] processAudio(byte[] audioData, VoiceProcessRequest request) throws IOException {
        logger.info("Processing audio: {} bytes", audioData.length);

        try {
            // First, detect if audio is already in the target format
            AudioFormatInfo formatInfo = detectAudioFormat(audioData);
            logger.info("Detected format: {} Hz, {} channels, {} bits, format: {}",
                    formatInfo.sampleRate, formatInfo.channels, formatInfo.sampleSize, formatInfo.encoding);

            short[] pcmData;

            // Check if already in target format (8kHz, 16-bit, mono WAV)
            if (formatInfo.sampleRate == SAMPLE_RATE &&
                formatInfo.channels == CHANNELS &&
                formatInfo.sampleSize == SAMPLE_SIZE &&
                formatInfo.encoding.equals("PCM_SIGNED")) {

                logger.info("Audio already in target format (8kHz WAV), skipping conversion");
                pcmData = extractPCMFromWav(audioData);
            } else {
                logger.info("Converting audio to target format (8kHz WAV)");
                // Convert to target format
                pcmData = convertToTargetFormat(audioData, formatInfo);
            }

            logger.info("Ready for processing: {} samples", pcmData.length);

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

    // Helper class to store audio format information
    private static class AudioFormatInfo {
        final float sampleRate;
        final int channels;
        final int sampleSize;
        final String encoding;
        final boolean isMP3;

        AudioFormatInfo(float sampleRate, int channels, int sampleSize, String encoding, boolean isMP3) {
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.sampleSize = sampleSize;
            this.encoding = encoding;
            this.isMP3 = isMP3;
        }
    }

    private AudioFormatInfo detectAudioFormat(byte[] audioData) throws IOException {
        // First, try to detect as MP3 by checking header
        if (isMP3Format(audioData)) {
            return new AudioFormatInfo(0, 0, 0, "MP3", true);
        }

        // Try to read as WAV using Java Sound API
        try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
             AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bais)) {

            AudioFormat format = audioInputStream.getFormat();
            return new AudioFormatInfo(
                    format.getSampleRate(),
                    format.getChannels(),
                    format.getSampleSizeInBits(),
                    format.getEncoding().toString(),
                    false
            );
        } catch (UnsupportedAudioFileException e) {
            logger.warn("Unable to detect audio format using Java Sound API, assuming MP3");
            return new AudioFormatInfo(0, 0, 0, "UNKNOWN", true);
        }
    }

    private boolean isMP3Format(byte[] audioData) {
        // Check for MP3 header signatures
        if (audioData.length < 3) return false;

        // ID3v2 tag
        if (audioData[0] == 'I' && audioData[1] == 'D' && audioData[2] == '3') {
            return true;
        }

        // MP3 frame header (sync word 0xFF followed by 0xE0-0xFF)
        if ((audioData[0] & 0xFF) == 0xFF && (audioData[1] & 0xE0) == 0xE0) {
            return true;
        }

        return false;
    }

    private short[] extractPCMFromWav(byte[] wavData) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(wavData);
             AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bais)) {

            // Read all data
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = audioInputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }

            byte[] pcmBytes = baos.toByteArray();
            short[] samples = new short[pcmBytes.length / 2];
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);

            return samples;
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Invalid WAV format", e);
        }
    }

    private short[] convertToTargetFormat(byte[] audioData, AudioFormatInfo formatInfo) throws IOException {
        if (formatInfo.isMP3) {
            return convertMP3ToTargetFormat(audioData);
        } else {
            return convertWavToTargetFormat(audioData);
        }
    }

    private short[] convertMP3ToTargetFormat(byte[] mp3Data) throws IOException {
        try {
            // Create temporary files for conversion
            Path inputPath = Files.createTempFile("input", ".mp3");
            Path outputPath = Files.createTempFile("output", ".wav");

            try {
                // Write input MP3 data
                Files.write(inputPath, mp3Data, StandardOpenOption.WRITE);

                // Build FFmpeg command to convert MP3 to 8kHz WAV
                FFmpegBuilder builder = new FFmpegBuilder()
                        .setInput(inputPath.toString())
                        .overrideOutputFiles(true)
                        .addOutput(outputPath.toString())
                        .setAudioCodec("pcm_s16le")
                        .setAudioSampleRate(SAMPLE_RATE)
                        .setAudioChannels(CHANNELS)
                        .done();

                FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
                executor.createJob(builder).run();

                // Read converted WAV file
                byte[] wavData = Files.readAllBytes(outputPath);
                return extractPCMFromWav(wavData);

            } finally {
                // Clean up temporary files
                Files.deleteIfExists(inputPath);
                Files.deleteIfExists(outputPath);
            }

        } catch (Exception e) {
            logger.error("Error converting MP3 to target format", e);
            throw new IOException("MP3 conversion failed: " + e.getMessage(), e);
        }
    }

    private short[] convertWavToTargetFormat(byte[] wavData) throws IOException {
        return convertToPCM16(wavData);
    }

    private short[] convertToPCM16(byte[] audioData) throws IOException {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bais);

            AudioFormat sourceFormat = audioInputStream.getFormat();
            logger.info("Source format: {} Hz, {} channels, {} bits",
                    sourceFormat.getSampleRate(), sourceFormat.getChannels(), sourceFormat.getSampleSizeInBits());

            // Define target format (16-bit PCM, mono, 8kHz)
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