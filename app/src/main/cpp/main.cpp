#include <jni.h>
#include <android/log.h>
#include <thread>
#include <atomic>
#include <memory>
#include "audio_capture.h"
#include "network_sender.h"

#define LOG_TAG "AAudioForwarder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static std::unique_ptr<AAudioCapture> audioCapture;
static std::unique_ptr<NetworkSender> networkSender;
static std::atomic<bool> isRunning{false};
static std::thread processingThread;

void audioProcessingLoop() {
    LOGI("Processing Thread Started.");
    
    // 1. TUNGGU PC CONNECT DULU (Blocking)
    // Audio tidak akan direkam sebelum PC nyolok dan connect.
    if (!networkSender->waitForConnection()) {
        LOGI("Wait for connection failed/cancelled.");
        return;
    }
    
    // 2. Start Microphone (AAudio)
    // Baru nyalain mic pas udah connect, biar buffer gak numpuk
    if (!audioCapture->start()) {
        LOGI("Failed to start AAudio");
        return;
    }
    
    std::vector<int16_t> audioBuffer;
    audioBuffer.reserve(4096);
    
    LOGI("Starting Stream Loop...");
    
    while (isRunning) {
        // Kalau PC putus, loop ini akan tetap jalan tapi sendAudioPacket return false
        // Kita bisa tambahkan logic reconnect kalau mau, tapi simpelnya gini dulu.
        if (audioCapture->waitForAudioData(audioBuffer)) {
             networkSender->sendAudioPacket(audioBuffer);
        }
    }
    
    audioCapture->stop();
    LOGI("Audio loop stopped");
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_aaudio_forwarder_AudioForwardService_startForwarding(
    JNIEnv* env, jobject, jstring jIpAddress, jint port, jint sampleRate) {
    
    if (isRunning) return JNI_FALSE;
    
    // IP Address kita abaikan karena kita jadi Server (Listen ANY)
    LOGI("Starting Server on Port %d", port);
    
    audioCapture = std::make_unique<AAudioCapture>();
    networkSender = std::make_unique<NetworkSender>();
    
    // 1. Init Mic (Belum Start)
    if (!audioCapture->initialize(sampleRate, 2)) return JNI_FALSE;
    
    // 2. Buka Port Server (Bind & Listen)
    if (!networkSender->startServer(port)) return JNI_FALSE;
    
    // 3. Start Thread (Nanti thread yang akan accept connection)
    isRunning = true;
    processingThread = std::thread(audioProcessingLoop);
    
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_aaudio_forwarder_AudioForwardService_stopForwarding(JNIEnv* env, jobject) {
    if (!isRunning) return;
    
    isRunning = false;
    
    // Force stop socket biar thread yang lagi blocking di accept() atau send() bangun
    if (networkSender) networkSender->stop();
    if (audioCapture) audioCapture->stop(); // Stop mic
    
    if (processingThread.joinable()) processingThread.join();
    
    audioCapture.reset();
    networkSender.reset();
}

JNIEXPORT jstring JNICALL
Java_com_aaudio_forwarder_AudioForwardService_getStatus(JNIEnv* env, jobject) {
    return env->NewStringUTF(isRunning ? "Running (Server Mode)" : "Stopped");
}

}