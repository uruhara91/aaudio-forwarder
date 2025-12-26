#ifndef NETWORK_CLIENT_H
#define NETWORK_CLIENT_H

#include <atomic>
#include <cstddef>

class NetworkClient {
private:
    int clientSocket;
    std::atomic<bool> connected;

public:
    NetworkClient();
    ~NetworkClient();

    bool connectToServer(const char* host, int port);
    bool sendData(const void* data, size_t size);
    void disconnect();
    bool isConnected() const;
};

#endif