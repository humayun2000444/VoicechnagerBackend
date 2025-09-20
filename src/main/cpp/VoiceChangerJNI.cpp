#include "VoiceProcessor.h"
#include <jni.h>
#include <cstring>
#include <memory>
#include <vector>

// JNI method implementations
extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_voicechanger_nativelib_NativeVoiceProcessor_createProcessor(JNIEnv *env, jobject obj) {
    try {
        VoiceProcessor* processor = new VoiceProcessor();
        return reinterpret_cast<jlong>(processor);
    } catch (const std::exception& e) {
        // Log error or throw Java exception
        jclass exClass = env->FindClass("java/lang/RuntimeException");
        if (exClass != nullptr) {
            env->ThrowNew(exClass, e.what());
        }
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_com_example_voicechanger_nativelib_NativeVoiceProcessor_destroyProcessor(JNIEnv *env, jobject obj, jlong handle) {
    if (handle != 0) {
        VoiceProcessor* processor = reinterpret_cast<VoiceProcessor*>(handle);
        delete processor;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_example_voicechanger_nativelib_NativeVoiceProcessor_configureProcessor(JNIEnv *env, jobject obj, jlong handle, jint sampleRate, jint maxBufferLength) {
    if (handle == 0) return JNI_FALSE;

    try {
        VoiceProcessor* processor = reinterpret_cast<VoiceProcessor*>(handle);
        processor->configure(static_cast<float>(sampleRate), static_cast<size_t>(maxBufferLength));
        return JNI_TRUE;
    } catch (const std::exception& e) {
        jclass exClass = env->FindClass("java/lang/RuntimeException");
        if (exClass != nullptr) {
            env->ThrowNew(exClass, e.what());
        }
        return JNI_FALSE;
    }
}

JNIEXPORT jshortArray JNICALL
Java_com_example_voicechanger_nativelib_NativeVoiceProcessor_processAudioNative(JNIEnv *env, jobject obj, jlong handle, jshortArray inputBuffer, jint length) {
    if (handle == 0 || inputBuffer == nullptr || length <= 0) {
        return nullptr;
    }

    try {
        VoiceProcessor* processor = reinterpret_cast<VoiceProcessor*>(handle);

        // Get input data from Java array
        jshort* inputData = env->GetShortArrayElements(inputBuffer, nullptr);
        if (inputData == nullptr) {
            return nullptr;
        }

        // Convert jshort to int16_t
        std::vector<int16_t> input(length);
        for (int i = 0; i < length; ++i) {
            input[i] = static_cast<int16_t>(inputData[i]);
        }

        // Process audio
        std::vector<int16_t> output = processor->process(input.data(), length);

        // Release input array
        env->ReleaseShortArrayElements(inputBuffer, inputData, JNI_ABORT);

        // Create output Java array
        jshortArray outputArray = env->NewShortArray(output.size());
        if (outputArray == nullptr) {
            return nullptr;
        }

        // Convert int16_t back to jshort and copy to Java array
        std::vector<jshort> outputData(output.size());
        for (size_t i = 0; i < output.size(); ++i) {
            outputData[i] = static_cast<jshort>(output[i]);
        }

        env->SetShortArrayRegion(outputArray, 0, output.size(), outputData.data());

        return outputArray;

    } catch (const std::exception& e) {
        jclass exClass = env->FindClass("java/lang/RuntimeException");
        if (exClass != nullptr) {
            env->ThrowNew(exClass, e.what());
        }
        return nullptr;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_example_voicechanger_nativelib_NativeVoiceProcessor_setSettings(JNIEnv *env, jobject obj, jlong handle, jfloat shiftSemitones, jfloat formantSemitones, jfloat formantBaseHz) {
    if (handle == 0) return JNI_FALSE;

    try {
        VoiceProcessor* processor = reinterpret_cast<VoiceProcessor*>(handle);
        processor->setSettings(shiftSemitones, formantSemitones, formantBaseHz);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        jclass exClass = env->FindClass("java/lang/RuntimeException");
        if (exClass != nullptr) {
            env->ThrowNew(exClass, e.what());
        }
        return JNI_FALSE;
    }
}

} // extern "C"