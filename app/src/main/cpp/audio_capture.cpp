#include "audio_capture.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "AAudioCapture"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Constructor
AAudioCapture::AAudioCapture() {}

// Destructor
AAudioCapture::~AAudioCapture() {
    stop();
    if (stream) {
        AAudioStream_close(stream);
        stream = nullptr;
    }
}

// Callbacks
aaudio_data_callback_result_t AAudioCapture::dataCallback(
    AAudioStream* stream,
    void* userData,
    void* audioData,
    int32_t numFrames) {
    
    auto* capture = static_cast<AAudioCapture*>(userData);
    auto* data = static_cast<int16_t*>(audioData);
    
    // Stereo = numFrames * 2
    std::vector<int16_t> buffer(data, data + numFrames * 2); 
    
    {
        std::lock_guard<std::mutex> lock(capture->queueMutex);
        if (capture->audioQueue.size() < 10) { 
            capture->audioQueue.push(std::move(buffer));
        }
    }
    
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void AAudioCapture::errorCallback(
    AAudioStream* stream,
    void* userData,
    aaudio_result_t error) {
    LOGE("AAudio error: %s", AAudio_convertResultToText(error));
}

// Initialization
bool AAudioCapture::initialize(int sampleRate, int channelCount) {
    AAudioStreamBuilder* builder = nullptr;
    
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);
    if (result != AAUDIO_OK) {
        LOGE("Failed to create stream builder: %s", AAudio_convertResultToText(result));
        return false;
    }
    
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    AAudioStreamBuilder_setChannelCount(builder, channelCount);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    
    // Set callbacks
    AAudioStreamBuilder_setDataCallback(builder, dataCallback, this);
    AAudioStreamBuilder_setErrorCallback(builder, errorCallback, this);
    
    // Buffer optimization
    AAudioStreamBuilder_setFramesPerDataCallback(builder, 192);
    
    result = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);
    
    if (result != AAUDIO_OK) {
        LOGE("Failed to open stream: %s", AAudio_convertResultToText(result));
        return false;
    }
    
    return true;
}

// Start
bool AAudioCapture::start() {
    if (!stream) return false;
    aaudio_result_t result = AAudioStream_requestStart(stream);
    if (result != AAUDIO_OK) return false;
    isRunning = true;
    return true;
}

// Stop
void AAudioCapture::stop() {
    if (stream && isRunning) {
        AAudioStream_requestStop(stream);
        isRunning = false;
    }
}

// Get Audio Data
bool AAudioCapture::getAudioData(std::vector<int16_t>& outData) {
    std::lock_guard<std::mutex> lock(queueMutex);
    if (audioQueue.empty()) return false;
    
    outData = std::move(audioQueue.front());
    audioQueue.pop();
    return true;
}
