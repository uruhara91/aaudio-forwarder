#include <jni.h>
#include <android/log.h>
#include <thread>
#include <atomic>
#include <memory>

// #include "audio_capture.cpp"
// #include "network_sender.cpp"

#define LOG_TAG "AAudioForwarder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global instances
static std::unique_ptr<AAudioCapture> audioCapture;
static std::unique_ptr<NetworkSender> networkSender;
static std::atomic<bool> isRunning{false};
static std::thread processingThread;

// Main processing loop
void audioProcessingLoop() {
    LOGI("Audio processing loop started");
    
    std::vector<int16_t> audioBuffer;
    
    while (isRunning) {
        // Get audio data dari capture
        if (audioCapture->getAudioData(audioBuffer)) {
            // Send ke network
            if (!networkSender->sendAudioPacket(audioBuffer)) {
                LOGE("Failed to send audio packet");
                // Bisa implement retry logic di sini
            }
        } else {
            // No data available, short sleep
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }
    
    LOGI("Audio processing loop stopped");
}

// JNI exports untuk dipanggil dari Java
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_aaudio_forwarder_AudioForwardService_startForwarding(
    JNIEnv* env,
    jobject /* this */,
    jstring jIpAddress,
    jint port,
    jint sampleRate) {
    
    if (isRunning) {
        LOGE("Already running");
        return JNI_FALSE;
    }
    
    // Convert Java string ke C string
    const char* ipAddress = env->GetStringUTFChars(jIpAddress, nullptr);
    
    LOGI("Starting audio forwarding to %s:%d @ %dHz", ipAddress, port, sampleRate);
    
    // Initialize components
    audioCapture = std::make_unique<AAudioCapture>();
    networkSender = std::make_unique<NetworkSender>();
    
    // Setup audio capture
    if (!audioCapture->initialize(sampleRate, 2)) { // 2 = stereo
        LOGE("Failed to initialize audio capture");
        env->ReleaseStringUTFChars(jIpAddress, ipAddress);
        return JNI_FALSE;
    }
    
    // Connect to PC
    if (!networkSender->connect(ipAddress, port)) {
        LOGE("Failed to connect to %s:%d", ipAddress, port);
        env->ReleaseStringUTFChars(jIpAddress, ipAddress);
        return JNI_FALSE;
    }
    
    env->ReleaseStringUTFChars(jIpAddress, ipAddress);
    
    // Start capture
    if (!audioCapture->start()) {
        LOGE("Failed to start audio capture");
        return JNI_FALSE;
    }
    
    // Start processing thread
    isRunning = true;
    processingThread = std::thread(audioProcessingLoop);
    
    LOGI("Audio forwarding started successfully");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_aaudio_forwarder_AudioForwardService_stopForwarding(
    JNIEnv* env,
    jobject /* this */) {
    
    if (!isRunning) {
        return;
    }
    
    LOGI("Stopping audio forwarding");
    
    // Stop processing loop
    isRunning = false;
    
    // Wait for thread to finish
    if (processingThread.joinable()) {
        processingThread.join();
    }
    
    // Cleanup
    if (audioCapture) {
        audioCapture->stop();
        audioCapture.reset();
    }
    
    if (networkSender) {
        networkSender->disconnect();
        networkSender.reset();
    }
    
    LOGI("Audio forwarding stopped");
}

JNIEXPORT jstring JNICALL
Java_com_aaudio_forwarder_AudioForwardService_getStatus(
    JNIEnv* env,
    jobject /* this */) {
    
    if (!isRunning) {
        return env->NewStringUTF("Stopped");
    }
    
    if (!audioCapture || !networkSender) {
        return env->NewStringUTF("Error: Components not initialized");
    }
    
    if (!networkSender->isActive()) {
        return env->NewStringUTF("Error: Network disconnected");
    }
    
    return env->NewStringUTF("Running");
}

// Alternative untuk command line usage (debugging)
JNIEXPORT jint JNICALL
Java_com_aaudio_forwarder_MainActivity_testAudio(
    JNIEnv* env,
    jobject /* this */) {
    
    LOGI("Testing AAudio support...");
    
    AAudioCapture test;
    if (!test.initialize(48000, 2)) {
        LOGE("AAudio not supported on this device");
        return -1;
    }
    
    LOGI("AAudio test successful");
    return 0;
}

} // extern "C"
