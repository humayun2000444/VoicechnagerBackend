#pragma once

#include "../signalsmith-stretch.h"
#include "./stop-denormals.h"

/* Vocal Shifting wrapper class

Each connection being processed needs a separate instance of this class.  This could be allocated on-the-fly, or re-used from a pool for increased efficiency.  Let's assume you somehow have one of these:

	StretchVocal &stretcher

When a connection is initialsed, you need to configure the stretcher with the sample-rate and the maximum number of audio frames which will be processed at once (a.k.a. packet length):

	stretcher.configure(connection.sampleRate, maxPacketLength);

This is also a good moment to set the shifter's settings.  Nishant will give you the appropriate values for this, but here are some example settings:

	// Male -> Female
	stretcher. settings(10, 2, 100);

	// Female -> Male
	stretcher. settings(-10, -2, 200);

Then for each incoming packet, which I've been informed is a 16-bit signed integer, you call `.process()`.  This can be done either in-place:

	int16_t *data = packet.getSamples();
	stretcher.process(data, packet.length);

Or out-of-place:

	const int16_t *inData = packet.inputSamples();
	int16_t *outData = packet.outputSamples();
	stretcher.process(inData, outData, packet.length);
 */
struct StretchVocal {

	void configure(float sampleRate, size_t maxBufferLength) {
		stretch.configure(1, sampleRate*0.06, sampleRate*0.015, true);
		floatBufferIn.resize(maxBufferLength);
		floatBufferOut.resize(maxBufferLength);
		limiterSlew = 1/(0.05*sampleRate + 1);
		sRate = sampleRate;

		settings(12, 3, 100);
	}
	void settings(float shiftSemitones, float formantSemitones, float formantBaseHz) {
		stretch.setTransposeSemitones(shiftSemitones, 6000/sRate);
		stretch.setFormantSemitones(formantSemitones, true);
		stretch.setFormantBase(formantBaseHz/sRate);
	}

	void reset() {
		stretch.reset();
		limiterGain = 1;
	}

	// In-place processing is supported
	void process(int16_t *buffer, size_t length) {
		process(buffer, buffer, length);
	}

	void process(int16_t *inBuffer, int16_t *outBuffer, size_t length) {
		StopDenormals scoped;

		// 16-bit to float
		for (size_t i = 0; i < length; ++i) {
			floatBufferIn[i] = inBuffer[i];
		}

		stretch.process(&floatBufferIn, length, &floatBufferOut, length);

		// float to 16-bit, with a basic limiter
		for (size_t i = 0; i < length; ++i) {
			float x = floatBufferOut[i];
			limiterGain += (1 - limiterGain)*limiterSlew;
			float y = x*limiterGain, absY = std::abs(y);
			if (absY > maxOutput) {
				limiterGain = maxOutput/absY;
				y = x*limiterGain;
			}
			outBuffer[i] = y;
		}
	}

private:
	float sRate = 1;
	std::vector<float> floatBufferIn, floatBufferOut;
	static constexpr float maxOutput = 32760;
	float limiterGain = 1, limiterSlew = 1;
	signalsmith::stretch::SignalsmithStretch<float> stretch;
};