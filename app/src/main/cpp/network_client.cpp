#include "network_client.h"
#include <unistd.h>
#include <fcntl.h>
#include <android/log.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <cstring>
#include <cerrno>

#define TAG "NetClient"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

NetworkClient::NetworkClient() : clientSocket(-1), connected(false) {}

NetworkClient::~NetworkClient() {
    disconnect();
}

bool NetworkClient::connectToServer(const char* host, int port) {
    if (connected) disconnect();

    clientSocket = socket(AF_INET, SOCK_STREAM, 0);
    if (clientSocket < 0) {
        LOGE("Socket creation failed: %s", strerror(errno));
        return false;
    }

    // Performance optimizations
    int flag = 1;
    setsockopt(clientSocket, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag));
    
    int bufSize = 262144; // 256KB send buffer
    setsockopt(clientSocket, SOL_SOCKET, SO_SNDBUF, &bufSize, sizeof(bufSize));

    // Non-blocking mode untuk timeout
    int flags = fcntl(clientSocket, F_GETFL, 0);
    fcntl(clientSocket, F_SETFL, flags | O_NONBLOCK);

    struct sockaddr_in serverAddr;
    memset(&serverAddr, 0, sizeof(serverAddr));
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(port);
    
    if (inet_pton(AF_INET, host, &serverAddr.sin_addr) <= 0) {
        LOGE("Invalid address: %s", host);
        close(clientSocket);
        clientSocket = -1;
        return false;
    }

    LOGI("Connecting to %s:%d...", host, port);
    int result = connect(clientSocket, (struct sockaddr*)&serverAddr, sizeof(serverAddr));
    
    if (result < 0 && errno != EINPROGRESS) {
        LOGE("Connect failed: %s", strerror(errno));
        close(clientSocket);
        clientSocket = -1;
        return false;
    }

    // Wait for connection with timeout (5 seconds)
    fd_set writeSet;
    FD_ZERO(&writeSet);
    FD_SET(clientSocket, &writeSet);
    
    struct timeval timeout;
    timeout.tv_sec = 5;
    timeout.tv_usec = 0;
    
    result = select(clientSocket + 1, nullptr, &writeSet, nullptr, &timeout);
    if (result <= 0) {
        LOGE("Connection timeout");
        close(clientSocket);
        clientSocket = -1;
        return false;
    }

    // Back to blocking mode
    fcntl(clientSocket, F_SETFL, flags);

    // Verify connection success
    int error;
    socklen_t len = sizeof(error);
    getsockopt(clientSocket, SOL_SOCKET, SO_ERROR, &error, &len);
    
    if (error != 0) {
        LOGE("Connection error: %s", strerror(error));
        close(clientSocket);
        clientSocket = -1;
        return false;
    }

    connected = true;
    LOGI("Connected successfully!");
    return true;
}

bool NetworkClient::sendData(const void* data, size_t size) {
    if (!connected || clientSocket < 0) return false;

    size_t totalSent = 0;
    const char* buffer = static_cast<const char*>(data);

    while (totalSent < size) {
        ssize_t sent = send(clientSocket, buffer + totalSent, size - totalSent, MSG_NOSIGNAL);
        
        if (sent < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                // Buffer full, wait a bit
                usleep(100);
                continue;
            }
            LOGE("Send failed: %s", strerror(errno));
            disconnect();
            return false;
        }
        
        totalSent += sent;
    }

    return true;
}

void NetworkClient::disconnect() {
    if (clientSocket >= 0) {
        shutdown(clientSocket, SHUT_RDWR);
        close(clientSocket);
        clientSocket = -1;
    }
    connected = false;
}

bool NetworkClient::isConnected() const {
    return connected && clientSocket >= 0;
}