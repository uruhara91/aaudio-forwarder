#include "network_client.h"
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <cstring>
#include <fcntl.h> 

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
    setsockopt(clientSocket, IPPROTO_TCP, TCP_NODELAY, (char *)&flag, sizeof(flag));
    
    // 2. Buffer optimization
    int bufSize = 16 * 1024; 
    setsockopt(clientSocket, SOL_SOCKET, SO_SNDBUF, (char *)&bufSize, sizeof(bufSize));

    struct sockaddr_in serverAddr;
    memset(&serverAddr, 0, sizeof(serverAddr));
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_port = htons(port);
    
    if (inet_pton(AF_INET, host, &serverAddr.sin_addr) <= 0) {
        close(clientSocket);
        return false;
    }

    // Blocking connect with timeout handled by caller or OS
    if (connect(clientSocket, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) < 0) {
        close(clientSocket);
        return false;
    }

    connected = true;
    return true;
}

bool NetworkClient::sendData(const void* data, size_t size) {
    if (!connected || clientSocket < 0) return false;

    // Blocking send is actually preferred here to ensure order and buffer management
    // But we use MSG_NOSIGNAL to prevent SIGPIPE crash
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