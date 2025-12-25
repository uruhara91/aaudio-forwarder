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

// Constructor
NetworkSender::NetworkSender() {}

// Destructor
NetworkSender::~NetworkSender() {
    disconnect();
}

// Connect UDP
bool NetworkSender::connect(const char* ipAddress, int port) {
    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    if (sockfd < 0) {
        LOGE("Failed to create socket: %s", strerror(errno));
        return false;
    }
    
    int optval = 1;
    setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &optval, sizeof(optval));
    
    int sendbuf = 256 * 1024; // 256KB
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

// Send Audio Packet
bool NetworkSender::sendAudioPacket(const std::vector<int16_t>& audioData) {
    if (!isConnected || sockfd < 0) return false;
    
    uint32_t dataSize = audioData.size() * sizeof(int16_t);
    uint32_t timestamp = static_cast<uint32_t>(
        std::chrono::steady_clock::now().time_since_epoch().count()
    );
    
    std::vector<uint8_t> packet(8 + dataSize);
    memcpy(packet.data(), &dataSize, 4);
    memcpy(packet.data() + 4, &timestamp, 4);
    memcpy(packet.data() + 8, audioData.data(), dataSize);
    
    ssize_t sent = sendto(sockfd, packet.data(), packet.size(), 0,
                         (struct sockaddr*)&serverAddr, sizeof(serverAddr));
    
    if (sent < 0) {
        errors++;
        return false;
    }
    
    bytesSent += sent;
    packetsSent++;
    
    if (packetsSent % 1000 == 0) {
        LOGI("Stats: %llu bytes, %u packets, %u errors",
             (unsigned long long)bytesSent.load(),
             packetsSent.load(),
             errors.load());
    }
    
    return true;
}

// Connect TCP (Optional but implemented)
bool NetworkSender::connectTCP(const char* ipAddress, int port) {
    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) {
        LOGE("Failed to create TCP socket: %s", strerror(errno));
        return false;
    }
    
    int flag = 1;
    setsockopt(sockfd, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag));
    
    int keepalive = 1;
    setsockopt(sockfd, SOL_SOCKET, SO_KEEPALIVE, &keepalive, sizeof(keepalive));
    
    memset(&serverAddr, 0, sizeof(serverAddr));
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(port);
    inet_pton(AF_INET, ipAddress, &serverAddr.sin_addr);
    
    if (::connect(sockfd, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) < 0) {
        LOGE("TCP connect failed: %s", strerror(errno));
        close(sockfd);
        sockfd = -1;
        return false;
    }
    
    isConnected = true;
    LOGI("TCP connected to %s:%d", ipAddress, port);
    return true;
}

// Send TCP
bool NetworkSender::sendTCP(const std::vector<int16_t>& audioData) {
    if (!isConnected || sockfd < 0) return false;
    uint32_t dataSize = audioData.size() * sizeof(int16_t);
    
    if (send(sockfd, &dataSize, sizeof(dataSize), 0) < 0) return false;
    if (send(sockfd, audioData.data(), dataSize, 0) < 0) return false;
    
    bytesSent += sizeof(dataSize) + dataSize;
    packetsSent++;
    return true;
}

// Disconnect
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

// Is Active
bool NetworkSender::isActive() const {
    return isConnected.load();
}
