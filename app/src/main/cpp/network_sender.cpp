#include "network_sender.h"
#include <unistd.h>
#include <android/log.h>
#include <netinet/tcp.h> 
#include <chrono>
#include <cstring>
#include <cerrno>

#define LOG_TAG "NetworkSender"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

NetworkSender::NetworkSender() {
    packetBuffer.reserve(4096 + 16); 
}

NetworkSender::~NetworkSender() {
    disconnect();
}

bool NetworkSender::connect(const char* ipAddress, int port) {
    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    if (sockfd < 0) {
        LOGE("Failed to create socket: %s", strerror(errno));
        return false;
    }
    
    int optval = 1;
    setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &optval, sizeof(optval));
    
    int sendbuf = 256 * 1024; 
    setsockopt(sockfd, SOL_SOCKET, SO_SNDBUF, &sendbuf, sizeof(sendbuf));
    
    memset(&serverAddr, 0, sizeof(serverAddr));
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(port);
    
    if (inet_pton(AF_INET, ipAddress, &serverAddr.sin_addr) <= 0) {
        LOGE("Invalid IP address: %s", ipAddress);
        close(sockfd);
        sockfd = -1;
        return false;
    }
    
    isConnected = true;
    LOGI("Connected to %s:%d", ipAddress, port);
    return true;
}

bool NetworkSender::sendAudioPacket(const std::vector<int16_t>& audioData) {
    if (!isConnected || sockfd < 0) return false;
    
    uint32_t dataSize = audioData.size() * sizeof(int16_t);
    uint32_t totalSize = 8 + dataSize;
    
    if (packetBuffer.size() < totalSize) {
        packetBuffer.resize(totalSize);
    }
    
    uint32_t timestamp = static_cast<uint32_t>(
        std::chrono::steady_clock::now().time_since_epoch().count()
    );
    
    memcpy(packetBuffer.data(), &dataSize, 4);
    memcpy(packetBuffer.data() + 4, &timestamp, 4);
    
    memcpy(packetBuffer.data() + 8, audioData.data(), dataSize);
    
    ssize_t sent = sendto(sockfd, packetBuffer.data(), totalSize, 0,
                         (struct sockaddr*)&serverAddr, sizeof(serverAddr));
    
    if (sent < 0) {
        errors++;
        return false;
    }
    
    bytesSent += sent;
    packetsSent++;
    
    if (packetsSent % 5000 == 0) {
        LOGI("Stats: %llu bytes, %u packets",
             (unsigned long long)bytesSent.load(),
             packetsSent.load());
    }
    
    return true;
}

bool NetworkSender::connectTCP(const char* ipAddress, int port) {
    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) return false;
    
    int flag = 1;
    setsockopt(sockfd, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag));
    
    memset(&serverAddr, 0, sizeof(serverAddr));
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(port);
    inet_pton(AF_INET, ipAddress, &serverAddr.sin_addr);
    
    if (::connect(sockfd, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) < 0) {
        close(sockfd);
        sockfd = -1;
        return false;
    }
    isConnected = true;
    return true;
}

bool NetworkSender::sendTCP(const std::vector<int16_t>& audioData) {
    if (!isConnected || sockfd < 0) return false;
    uint32_t dataSize = audioData.size() * sizeof(int16_t);
    
    if (send(sockfd, &dataSize, sizeof(dataSize), 0) < 0) return false;
    if (send(sockfd, audioData.data(), dataSize, 0) < 0) return false;
    
    bytesSent += sizeof(dataSize) + dataSize;
    packetsSent++;
    return true;
}

void NetworkSender::disconnect() {
    if (sockfd >= 0) {
        close(sockfd);
        sockfd = -1;
    }
    isConnected = false;
    LOGI("Disconnected. Final stats: %llu bytes, %u packets",
         (unsigned long long)bytesSent.load(),
         packetsSent.load());
}

bool NetworkSender::isActive() const {
    return isConnected.load();
}