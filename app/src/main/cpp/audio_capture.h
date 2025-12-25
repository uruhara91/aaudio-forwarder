#ifndef AUDIO_CAPTURE_H
#define AUDIO_CAPTURE_H

#include <aaudio/AAudio.h>
#include <vector>
#include <atomic>
#include <mutex>
#include <condition_variable>

class AAudioCapture {
private:
    AAudioStream* stream = nullptr;
    std::atomic<bool> isRunning{false};
    
    // Optimasi: Buffer tunggal & Sinyal thread
    std::vector<int16_t> internalBuffer;
    bool hasNewData = false;
    
    std::mutex dataMutex;
    std::condition_variable dataCondition;
    
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
    
    // Fungsi baru: Blocking wait (Hemat CPU)
    bool waitForAudioData(std::vector<int16_t>& outData);
};

#endif // AUDIO_CAPTURE_H