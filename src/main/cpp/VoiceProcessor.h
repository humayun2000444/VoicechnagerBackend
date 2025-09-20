//#endif // VOICE_PROCESSOR_H
#ifndef VOICE_PROCESSOR_H
#define VOICE_PROCESSOR_H

#include <cstdint>
#include <vector>
#include <memory>

// Forward declaration
class StretchVocal;

class VoiceProcessor {
public:
    VoiceProcessor();
    ~VoiceProcessor();

    void configure(float sampleRate, size_t maxBufferLength);
    void setSettings(float shiftSemitones, float formantSemitones, float formantBaseHz);
    std::vector<int16_t> process(const int16_t* inputBuffer, size_t length);
    void reset();
    bool isConfigured() const { return configured; }

private:
    std::unique_ptr<StretchVocal> stretcher;
    bool configured;
    float sampleRate;
    size_t maxBufferLength;

    // Current settings
    float currentShift;
    float currentFormant;
    float currentBase;

    // Buffer for processing (int16_t since StretchVocal uses int16_t)
    std::vector<int16_t> int16Buffer;
};

#endif // VOICE_PROCESSOR_H