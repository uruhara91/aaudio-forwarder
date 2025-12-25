#include <aaudio/AAudio.h>
#include <android/log.h>
#include <cstring>
#include <atomic>
#include <queue>
#include <mutex>

#define LOG_TAG "AAudioCapture"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class AAudioCapture {
private:
    AAudioStream* stream = nullptr;
    std::atomic<bool> isRunning{false};
    
    // Ring buffer untuk menghindari blocking
    static constexpr size_t BUFFER_SIZE = 8192;
    std::queue<std::vector<int16_t>> audioQueue;
    std::mutex queueMutex;
    
    // Audio callback (dipanggil di real-time thread)
    static aaudio_data_callback_result_t dataCallback(
        AAudioStream* stream,
        void* userData,
        void* audioData,
        int32_t numFrames) {
        
        auto* capture = static_cast<AAudioCapture*>(userData);
        auto* data = static_cast<int16_t*>(audioData);
        
        // Copy ke queue untuk processing di thread lain
        std::vector<int16_t> buffer(data, data + numFrames * 2); // stereo
        
        {
            std::lock_guard<std::mutex> lock(capture->queueMutex);
            if (capture->audioQueue.size() < 10) { // Limit queue size
                capture->audioQueue.push(std::move(buffer));
            }
        }
        
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }
    
    static void errorCallback(
        AAudioStream* stream,
        void* userData,
        aaudio_result_t error) {
        LOGE("AAudio error: %s", AAudio_convertResultToText(error));
    }

public:
    bool initialize(int sampleRate = 48000, int channelCount = 2) {
        AAudioStreamBuilder* builder = nullptr;
        
        aaudio_result_t result = AAudio_createStreamBuilder(&builder);
        if (result != AAUDIO_OK) {
            LOGE("Failed to create stream builder: %s", 
                 AAudio_convertResultToText(result));
            return false;
        }
        
        // Konfigurasi untuk low latency capture
        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
        AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_setSampleRate(builder, sampleRate);
        AAudioStreamBuilder_setChannelCount(builder, channelCount);
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
        
        // Set callbacks
        AAudioStreamBuilder_setDataCallback(builder, dataCallback, this);
        AAudioStreamBuilder_setErrorCallback(builder, errorCallback, this);
        
        // Optimasi buffer
        AAudioStreamBuilder_setFramesPerDataCallback(builder, 192); // ~4ms @ 48kHz
        
        result = AAudioStreamBuilder_openStream(builder, &stream);
        AAudioStreamBuilder_delete(builder);
        
        if (result != AAUDIO_OK) {
            LOGE("Failed to open stream: %s", AAudio_convertResultToText(result));
            return false;
        }
        
        // Log actual configuration
        LOGI("Stream opened:");
        LOGI("  Sample rate: %d", AAudioStream_getSampleRate(stream));
        LOGI("  Channel count: %d", AAudioStream_getChannelCount(stream));
        LOGI("  Frames per burst: %d", AAudioStream_getFramesPerBurst(stream));
        LOGI("  Buffer capacity: %d", AAudioStream_getBufferCapacityInFrames(stream));
        
        return true;
    }
    
    bool start() {
        if (!stream) return false;
        
        aaudio_result_t result = AAudioStream_requestStart(stream);
        if (result != AAUDIO_OK) {
            LOGE("Failed to start stream: %s", AAudio_convertResultToText(result));
            return false;
        }
        
        isRunning = true;
        LOGI("Audio capture started");
        return true;
    }
    
    void stop() {
        if (stream && isRunning) {
            AAudioStream_requestStop(stream);
            isRunning = false;
            LOGI("Audio capture stopped");
        }
    }
    
    bool getAudioData(std::vector<int16_t>& outData) {
        std::lock_guard<std::mutex> lock(queueMutex);
        if (audioQueue.empty()) {
            return false;
        }
        
        outData = std::move(audioQueue.front());
        audioQueue.pop();
        return true;
    }
    
    ~AAudioCapture() {
        stop();
        if (stream) {
            AAudioStream_close(stream);
            stream = nullptr;
        }
    }
};
