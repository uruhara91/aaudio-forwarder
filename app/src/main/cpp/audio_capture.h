#ifndef AUDIO_CAPTURE_H
#define AUDIO_CAPTURE_H

#include <aaudio/AAudio.h>
#include <vector>
#include <atomic>
#include <queue>
#include <mutex>

class AAudioCapture {
private:
    AAudioStream* stream = nullptr;
    std::atomic<bool> isRunning{false};
    
    // Ring buffer components
    std::queue<std::vector<int16_t>> audioQueue;
    std::mutex queueMutex;
    
    // Callbacks
    static aaudio_data_callback_result_t dataCallback(
        AAudioStream* stream,
        void* userData,
        void* audioData,
        int32_t numFrames);
        
    static void errorCallback(
        AAudioStream* stream,
        void* userData,
        aaudio_result_t error);

public:
    AAudioCapture();
    ~AAudioCapture();
    
    bool initialize(int sampleRate, int channelCount);
    bool start();
    void stop();
    bool getAudioData(std::vector<int16_t>& outData);
};

#endif // AUDIO_CAPTURE_H
