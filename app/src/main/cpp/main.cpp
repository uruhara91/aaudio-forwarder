#include <jni.h>
#include <android/log.h>
#include <thread>
#include <atomic>
#include <memory>
#include "audio_capture.h"
#include "network_sender.h"

#define LOG_TAG "AAudioForwarder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::unique_ptr<AAudioCapture> audioCapture;
static std::unique_ptr<NetworkSender> networkSender;
static std::atomic<bool> isRunning{false};
static std::thread processingThread;

void audioProcessingLoop() {
    LOGI("Audio processing loop started (Event Driven)");
    
    std::vector<int16_t> audioBuffer;
    audioBuffer.reserve(4096);
    
    while (isRunning) {
        if (audioCapture->waitForAudioData(audioBuffer)) {
            if (!networkSender->sendAudioPacket(audioBuffer)) {
            }
        }
    }
    LOGI("Audio processing loop stopped");
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_aaudio_forwarder_AudioForwardService_startForwarding(
    JNIEnv* env, jobject, jstring jIpAddress, jint port, jint sampleRate) {
    
    if (isRunning) return JNI_FALSE;
    
    const char* ipAddress = env->GetStringUTFChars(jIpAddress, nullptr);
    LOGI("Starting forwarding -> %s:%d", ipAddress, port);
    
    audioCapture = std::make_unique<AAudioCapture>();
    networkSender = std::make_unique<NetworkSender>();
    
    if (!audioCapture->initialize(sampleRate, 2)) {
        env->ReleaseStringUTFChars(jIpAddress, ipAddress);
        return JNI_FALSE;
    }
    
    if (!networkSender->connect(ipAddress, port)) {
        env->ReleaseStringUTFChars(jIpAddress, ipAddress);
        return JNI_FALSE;
    }
    
    env->ReleaseStringUTFChars(jIpAddress, ipAddress);
    
    if (!audioCapture->start()) return JNI_FALSE;
    
    isRunning = true;
    processingThread = std::thread(audioProcessingLoop);
    
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_aaudio_forwarder_AudioForwardService_stopForwarding(JNIEnv* env, jobject) {
    if (!isRunning) return;
    
    isRunning = false;
    
    if (audioCapture) audioCapture->stop();
    
    if (processingThread.joinable()) processingThread.join();
    
    if (networkSender) networkSender->disconnect();
    
    audioCapture.reset();
    networkSender.reset();
}

JNIEXPORT jstring JNICALL
Java_com_aaudio_forwarder_AudioForwardService_getStatus(JNIEnv* env, jobject) {
    if (isRunning) return env->NewStringUTF("Running");
    return env->NewStringUTF("Stopped");
}

}