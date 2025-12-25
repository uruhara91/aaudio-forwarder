#ifndef NETWORK_SENDER_H
#define NETWORK_SENDER_H

#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <atomic>

class NetworkSender {
private:
    int serverSocket = -1;
    int clientSocket = -1;
    struct sockaddr_in address;
    std::atomic<bool> isConnected{false};

public:
    NetworkSender();
    ~NetworkSender();

    bool startServer(int port);
    bool waitForConnection();
    
    // VERSI OPTIMIZED: Terima raw pointer & size
    // Gak perlu construct vector dulu.
    bool sendRawData(const void* data, size_t size);
    
    void closeClient();
    void stop();
    bool isActive() const;
};

#endif