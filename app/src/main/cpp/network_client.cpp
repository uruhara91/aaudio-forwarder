#include "network_client.h"
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <cstring>

NetworkClient::NetworkClient() : clientSocket(-1), connected(false) {}

NetworkClient::~NetworkClient() {
    disconnect();
}

bool NetworkClient::connectToServer(const char* host, int port) {
    if (connected) disconnect();

    clientSocket = socket(AF_INET, SOCK_STREAM, 0);
    if (clientSocket < 0) return false;

    // 1. Disable Nagle's Algo
    int flag = 1;
    setsockopt(clientSocket, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag));
    
    // 2. Buffer secukupnya
    int bufSize = 64 * 1024; 
    setsockopt(clientSocket, SOL_SOCKET, SO_SNDBUF, &bufSize, sizeof(bufSize));

    // 3. Send Timeout (2 detik) agar tidak blocking selamanya jika error
    struct timeval timeout;
    timeout.tv_sec = 2;
    timeout.tv_usec = 0;
    setsockopt(clientSocket, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));

    struct sockaddr_in serverAddr;
    memset(&serverAddr, 0, sizeof(serverAddr));
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(port);
    
    if (inet_pton(AF_INET, host, &serverAddr.sin_addr) <= 0) {
        close(clientSocket);
        return false;
    }

    if (connect(clientSocket, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) < 0) {
        close(clientSocket);
        return false;
    }

    connected = true;
    return true;
}

bool NetworkClient::sendData(const void* data, size_t size) {
    if (!connected || clientSocket < 0) return false;

    // MSG_NOSIGNAL
    ssize_t sent = send(clientSocket, data, size, MSG_NOSIGNAL);
    
    if (sent != (ssize_t)size) {
        disconnect();
        return false;
    }

    return true;
}

void NetworkClient::disconnect() {
    bool expected = true;
    if (connected.compare_exchange_strong(expected, false)) {
        if (clientSocket >= 0) {
            shutdown(clientSocket, SHUT_RDWR);
            close(clientSocket);
            clientSocket = -1;
        }
    }
}

bool NetworkClient::isConnected() const {
    return connected;
}