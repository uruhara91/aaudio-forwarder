#include <jni.h>
#include <memory>
#include "network_client.h"

static std::unique_ptr<NetworkClient> client;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_aaudio_forwarder_AudioForwardService_connectToPC(
    JNIEnv* env, jobject, jstring host, jint port) {
    
    const char* hostStr = env->GetStringUTFChars(host, nullptr);
    
    client = std::make_unique<NetworkClient>();
    bool success = client->connectToServer(hostStr, port);
    
    env->ReleaseStringUTFChars(host, hostStr);
    
    if (!success) {
        client.reset();
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_aaudio_forwarder_AudioForwardService_sendAudioDirect(
    JNIEnv* env, jobject, jobject directBuffer, jint size) {
    
    if (!client || !client->isConnected()) return JNI_FALSE;
    
    void* data = env->GetDirectBufferAddress(directBuffer);
    if (!data) return JNI_FALSE;
    
    return client->sendData(data, size) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_aaudio_forwarder_AudioForwardService_closeConnection(JNIEnv*, jobject) {
    if (client) {
        client->disconnect();
        client.reset();
    }
}

}