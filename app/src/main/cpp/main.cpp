#include <jni.h>
#include <android/log.h>
#include <memory>
#include "network_sender.h"

static std::unique_ptr<NetworkSender> networkSender;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_aaudio_forwarder_AudioForwardService_initServer(JNIEnv* env, jobject, jint port) {
    networkSender = std::make_unique<NetworkSender>();
    if (!networkSender->startServer(port)) return JNI_FALSE;
    if (!networkSender->waitForConnection()) return JNI_FALSE;
    return JNI_TRUE;
}

// ZERO COPY JNI CALL
// Perhatikan parameternya: jobject directBuffer (bukan jbyteArray)
JNIEXPORT jboolean JNICALL
Java_com_aaudio_forwarder_AudioForwardService_sendPcmDataDirect(JNIEnv* env, jobject, jobject directBuffer, jint size) {
    if (!networkSender) return JNI_FALSE;

    // Ambil alamat memori fisik dari DirectByteBuffer
    // Ini cost-nya hampir nol, cuma pointer lookup.
    void* bufferAddr = env->GetDirectBufferAddress(directBuffer);
    
    if (bufferAddr == nullptr) {
        return JNI_FALSE; // Bukan Direct Buffer atau error
    }

    // Kirim langsung dari alamat itu!
    return networkSender->sendRawData(bufferAddr, (size_t)size) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_aaudio_forwarder_AudioForwardService_closeServer(JNIEnv* env, jobject) {
    if (networkSender) {
        networkSender->stop();
        networkSender.reset();
    }
}

}