#include "network_sender.h"
#include <unistd.h>
#include <android/log.h>
#include <netinet/tcp.h> 
#include <cstring>
#include <cerrno>

#define LOG_TAG "NetworkSender"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

NetworkSender::NetworkSender() {}
NetworkSender::~NetworkSender() { stop(); }

bool NetworkSender::startServer(int port) {
    serverSocket = socket(AF_INET, SOCK_STREAM, 0);
    if (serverSocket < 0) return false;
    
    int opt = 1;
    setsockopt(serverSocket, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    memset(&address, 0, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(port);
    
    if (bind(serverSocket, (struct sockaddr*)&address, sizeof(address)) < 0) {
        close(serverSocket); return false;
    }
    if (listen(serverSocket, 1) < 0) {
        close(serverSocket); return false;
    }
    return true;
}

bool NetworkSender::waitForConnection() {
    if (serverSocket < 0) return false;
    struct sockaddr_in clientAddr;
    socklen_t addrLen = sizeof(clientAddr);
    clientSocket = accept(serverSocket, (struct sockaddr*)&clientAddr, &addrLen);
    
    if (clientSocket < 0) return false;
    
    // Performance Tweak: No Delay
    int flag = 1;
    setsockopt(clientSocket, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag));
    
    // Biar gak SIGPIPE kalau client putus
    int set = 1;
    #ifdef SO_NOSIGPIPE
    setsockopt(clientSocket, SOL_SOCKET, SO_NOSIGPIPE, (void *)&set, sizeof(int));
    #endif

    isConnected = true;
    return true;
}

// FUNGSI INI SEKARANG SANGAT RINGAN
bool NetworkSender::sendRawData(const void* data, size_t size) {
    if (!isConnected || clientSocket < 0) return false;
    
    // Langsung kirim pointer buffer dari memory (tanpa copy)
    ssize_t sent = send(clientSocket, data, size, MSG_NOSIGNAL);
    
    if (sent < 0) {
        closeClient(); // Disconnect detected
        return false;
    }
    return true;
}

void NetworkSender::closeClient() {
    if (clientSocket >= 0) { close(clientSocket); clientSocket = -1; }
    isConnected = false;
}

void NetworkSender::stop() {
    closeClient();
    if (serverSocket >= 0) { close(serverSocket); serverSocket = -1; }
}

bool NetworkSender::isActive() const { return (serverSocket >= 0); }