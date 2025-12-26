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

    // Connect to PC server
    bool connectToServer(const char* host, int port);
    
    // Zero-copy send
    bool sendData(const void* data, size_t size);
    
    void disconnect();
    bool isConnected() const;
};

#endif // NETWORK_CLIENT_H