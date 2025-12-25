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
    stop();
}

bool NetworkSender::startServer(int port) {
    // 1. Bikin Socket Server
    serverSocket = socket(AF_INET, SOCK_STREAM, 0);
    if (serverSocket < 0) {
        LOGE("Failed to create server socket: %s", strerror(errno));
        return false;
    }
    
    // 2. Biar port bisa langsung dipake ulang kalau restart
    int opt = 1;
    setsockopt(serverSocket, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    // 3. Bind ke Port (Listen semua interface 0.0.0.0 atau Localhost)
    memset(&address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY; // Terima dari mana aja (USB via ADB Forward)
    address.sin_port = htons(port);
    
    if (bind(serverSocket, (struct sockaddr*)&address, sizeof(address)) < 0) {
        LOGE("Bind failed on port %d: %s", port, strerror(errno));
        close(serverSocket);
        serverSocket = -1;
        return false;
    }
    
    // 4. Mulai Listening (Max antrian 1 PC)
    if (listen(serverSocket, 1) < 0) {
        LOGE("Listen failed: %s", strerror(errno));
        close(serverSocket);
        serverSocket = -1;
        return false;
    }
    
    LOGI("Server listening on port %d...", port);
    return true;
}

bool NetworkSender::waitForConnection() {
    if (serverSocket < 0) return false;
    
    LOGI("Waiting for PC to connect...");
    
    // INI BLOCKING! Thread akan berhenti di sini sampai QtScrcpy connect.
    struct sockaddr_in clientAddr;
    socklen_t addrLen = sizeof(clientAddr);
    
    clientSocket = accept(serverSocket, (struct sockaddr*)&clientAddr, &addrLen);
    
    if (clientSocket < 0) {
        LOGE("Accept failed: %s", strerror(errno));
        return false;
    }
    
    // Matikan Nagle Algorithm biar Real-time
    int flag = 1;
    setsockopt(clientSocket, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag));
    
    isConnected = true;
    LOGI("PC Connected! (IP: %s)", inet_ntoa(clientAddr.sin_addr));
    return true;
}

bool NetworkSender::sendAudioPacket(const std::vector<int16_t>& audioData) {
    if (!isConnected || clientSocket < 0) return false;
    
    size_t dataSize = audioData.size() * sizeof(int16_t);
    
    // Kirim ke ClientSocket (PC)
    ssize_t sent = send(clientSocket, audioData.data(), dataSize, 0); // MSG_NOSIGNAL biar gak crash kalau putus
    
    if (sent < 0) {
        // Kalau PC disconnect (Stop Audio), kita reset status tapi Server tetep nyala
        LOGE("Send error (PC Disconnected?): %s", strerror(errno));
        closeClient();
        return false;
    }
    
    bytesSent += sent;
    return true;
}

void NetworkSender::closeClient() {
    if (clientSocket >= 0) {
        close(clientSocket);
        clientSocket = -1;
    }
    isConnected = false;
    LOGI("Client disconnected.");
}

void NetworkSender::stop() {
    closeClient();
    if (serverSocket >= 0) {
        close(serverSocket);
        serverSocket = -1;
    }
    LOGI("Server stopped.");
}

bool NetworkSender::isActive() const {
    // Kita anggap active kalau Server nyala (biarpun client belum connect)
    // Biar Audio Capture tetep jalan/standby.
    return (serverSocket >= 0); 
}