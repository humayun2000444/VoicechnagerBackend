#include "VoiceProcessor.h"
#include "signalsmith-stretch/plugin/stretch-vocal.h"
#include <algorithm>
#include <stdexcept>
#include <cmath>

VoiceProcessor::VoiceProcessor()
    : stretcher(std::make_unique<StretchVocal>())
    , configured(false)
    , sampleRate(44100.0f)
    , maxBufferLength(8192)
    , currentShift(0.0f)
    , currentFormant(0.0f)
    , currentBase(100.0f) {
}

VoiceProcessor::~VoiceProcessor() = default;

void VoiceProcessor::configure(float sampleRate, size_t maxBufferLength) {
    this->sampleRate = sampleRate;
    this->maxBufferLength = maxBufferLength;

    try {
        // Configure the StretchVocal processor
        stretcher->configure(sampleRate, maxBufferLength);

        // Allocate conversion buffers
        int16Buffer.resize(maxBufferLength);

        configured = true;

        // Set initial settings
        setSettings(10.0f, 2.0f, 100.0f);

    } catch (const std::exception& e) {
        configured = false;
        throw std::runtime_error("Failed to configure VoiceProcessor: " + std::string(e.what()));
    }
}

void VoiceProcessor::setSettings(float shiftSemitones, float formantSemitones, float formantBaseHz) {
    if (!configured) {
        throw std::runtime_error("VoiceProcessor not configured");
    }

    try {
        currentShift = shiftSemitones;
        currentFormant = formantSemitones;
        currentBase = formantBaseHz;

        // Apply settings to the stretcher
        stretcher->settings(shiftSemitones, formantSemitones, formantBaseHz);

    } catch (const std::exception& e) {
        throw std::runtime_error("Failed to set voice settings: " + std::string(e.what()));
    }
}

std::vector<int16_t> VoiceProcessor::process(const int16_t* inputBuffer, size_t length) {
    if (!configured) {
        throw std::runtime_error("VoiceProcessor not configured");
    }

    if (length > maxBufferLength) {
        throw std::runtime_error("Input buffer length exceeds maximum configured length");
    }

    try {
        // Copy input to internal buffer (StretchVocal processes in-place)
        std::copy(inputBuffer, inputBuffer + length, int16Buffer.data());

        // Process with StretchVocal - CORRECTED: using int16_t buffers
        stretcher->process(int16Buffer.data(), length);

        // Return processed audio
        return std::vector<int16_t>(int16Buffer.data(), int16Buffer.data() + length);

    } catch (const std::exception& e) {
        // On error, return the original input
        std::vector<int16_t> output(inputBuffer, inputBuffer + length);
        return output;
    }
}

void VoiceProcessor::reset() {
    if (!configured) return;

    try {
        stretcher->reset();
    } catch (const std::exception& e) {
        // Log error but don't throw - reset should be robust
    }
}