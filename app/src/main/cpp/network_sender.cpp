#include "network_sender.h"
#include <unistd.h>
#include <android/log.h>
#include <netinet/tcp.h> 
#include <cstring>
#include <cerrno>

#define LOG_TAG "NetworkSender"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

NetworkSender::NetworkSender() {}

NetworkSender::~NetworkSender() {
    disconnect();
}

bool NetworkSender::connect(const char* ipAddress, int port) {
    // FIX: Pakai SOCK_STREAM (TCP) buat ADB Reverse
    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) {
        LOGE("Failed to create socket: %s", strerror(errno));
        return false;
    }
    
    // Disable Nagle's Algorithm (Biar audio real-time gak ditahan-tahan)
    int flag = 1;
    setsockopt(sockfd, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag));
    
    memset(&serverAddr, 0, sizeof(serverAddr));
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(port);
    
    if (inet_pton(AF_INET, ipAddress, &serverAddr.sin_addr) <= 0) {
        LOGE("Invalid IP address: %s", ipAddress);
        close(sockfd);
        sockfd = -1;
        return false;
    }
    
    // TCP Wajib Connect dulu
    if (::connect(sockfd, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) < 0) {
        LOGE("Failed to connect to PC: %s (Is ADB Reverse running?)", strerror(errno));
        close(sockfd);
        sockfd = -1;
        return false;
    }
    
    isConnected = true;
    LOGI("TCP Connected to %s:%d", ipAddress, port);
    return true;
}

bool NetworkSender::sendAudioPacket(const std::vector<int16_t>& audioData) {
    if (!isConnected || sockfd < 0) return false;
    
    // Kirim RAW PCM Data (Tanpa Header Size/Timestamp)
    // Biar socat dan ffplay bisa langsung mainkan tanpa 'kresek-kresek'.
    size_t dataSize = audioData.size() * sizeof(int16_t);
    
    // TCP send
    ssize_t sent = send(sockfd, audioData.data(), dataSize, 0);
    
    if (sent < 0) {
        LOGE("Send error: %s", strerror(errno));
        // Kalau error pipe/disconnect, anggap putus
        if (errno == EPIPE || errno == ECONNRESET) {
            disconnect();
        }
        return false;
    }
    
    bytesSent += sent;
    return true;
}

void NetworkSender::disconnect() {
    if (sockfd >= 0) {
        close(sockfd);
        sockfd = -1;
    }
    isConnected = false;
    LOGI("Disconnected. Total bytes sent: %llu", (unsigned long long)bytesSent.load());
}

bool NetworkSender::isActive() const {
    return isConnected.load();
}