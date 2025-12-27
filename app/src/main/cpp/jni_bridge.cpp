#include <jni.h>
#include <memory>
#include "network_client.h"

static std::unique_ptr<NetworkClient> client;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_android_sound_helper_AudioForwardService_connectToPC(
    JNIEnv* env, jobject, jstring host, jint port) {
    
    const char* hostStr = env->GetStringUTFChars(host, nullptr);
    
    // Reset client
    client = std::make_unique<NetworkClient>();
    bool success = client->connectToServer(hostStr, port);
    
    env->ReleaseStringUTFChars(host, hostStr);
    
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_android_sound_helper_AudioForwardService_sendAudioDirect(
    JNIEnv* env, jobject, jobject directBuffer, jint size) {
    
    if (!client || !client->isConnected()) return JNI_FALSE;
    
    // ZERO COPY
    void* data = env->GetDirectBufferAddress(directBuffer);
    if (!data) return JNI_FALSE;
    
    return client->sendData(data, size) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_android_sound_helper_AudioForwardService_closeConnection(JNIEnv*, jobject) {
    if (client) {
        client->disconnect();
        client.reset();
    }
}

}