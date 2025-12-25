#include "audio_capture.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "AAudioCapture"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AAudioCapture::AAudioCapture() {}

AAudioCapture::~AAudioCapture() {
    stop();
    if (stream) {
        AAudioStream_close(stream);
        stream = nullptr;
    }
}

aaudio_data_callback_result_t AAudioCapture::dataCallback(
    AAudioStream* stream,
    void* userData,
    void* audioData,
    int32_t numFrames) {
    
    auto* capture = static_cast<AAudioCapture*>(userData);
    int32_t numSamples = numFrames * AAudioStream_getChannelCount(stream);
    
    // Critical Section: Super Cepat
    {
        std::lock_guard<std::mutex> lock(capture->dataMutex);
        
        // Resize buffer sekali saja di awal
        if (capture->internalBuffer.size() != numSamples) {
            capture->internalBuffer.resize(numSamples);
        }
        
        // Copy memory langsung (Zero Allocation saat runtime stabil)
        memcpy(capture->internalBuffer.data(), audioData, numSamples * sizeof(int16_t));
        capture->hasNewData = true;
    }
    
    // Bangunkan thread pengirim
    capture->dataCondition.notify_one();
    
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void AAudioCapture::errorCallback(
    AAudioStream* stream,
    void* userData,
    aaudio_result_t error) {
    LOGE("AAudio error: %s", AAudio_convertResultToText(error));
}

bool AAudioCapture::initialize(int sampleRate, int channelCount) {
    AAudioStreamBuilder* builder = nullptr;
    AAudio_createStreamBuilder(&builder);
    
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    AAudioStreamBuilder_setChannelCount(builder, channelCount);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setDataCallback(builder, dataCallback, this);
    AAudioStreamBuilder_setErrorCallback(builder, errorCallback, this);
    
    // Tuning buffer: Semakin kecil semakin low latency, tapi resiko glitch
    // 192 frames @ 48kHz = 4ms latency
    AAudioStreamBuilder_setFramesPerDataCallback(builder, 192);
    
    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);
    
    if (result != AAUDIO_OK) {
        LOGE("Open stream failed: %s", AAudio_convertResultToText(result));
        return false;
    }
    return true;
}

bool AAudioCapture::start() {
    if (!stream) return false;
    internalBuffer.reserve(4096); // Pre-allocation memory
    isRunning = true;
    return AAudioStream_requestStart(stream) == AAUDIO_OK;
}

void AAudioCapture::stop() {
    if (stream) {
        isRunning = false;
        // Wake up thread biar bisa exit
        dataCondition.notify_all(); 
        AAudioStream_requestStop(stream);
    }
}

bool AAudioCapture::waitForAudioData(std::vector<int16_t>& outData) {
    std::unique_lock<std::mutex> lock(dataMutex);
    
    // Tidur sampai ada data (Hemat baterai & CPU)
    dataCondition.wait(lock, [this]{ return hasNewData || !isRunning; });
    
    if (!isRunning) return false;
    
    // Tukar isi buffer (sangat cepat, O(1))
    outData = internalBuffer; // Copy is fine here for simplicity, or use swap
    hasNewData = false;
    
    return true;
}