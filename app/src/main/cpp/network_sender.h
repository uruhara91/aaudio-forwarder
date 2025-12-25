#ifndef NETWORK_SENDER_H
#define NETWORK_SENDER_H

#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <atomic>
#include <vector>
#include <string>

class NetworkSender {
private:
    int sockfd = -1;
    struct sockaddr_in serverAddr;
    std::atomic<bool> isConnected{false};
    
    std::atomic<uint64_t> bytesSent{0};

public:
    NetworkSender();
    ~NetworkSender();

    // Kita ubah implementasinya jadi TCP di file .cpp
    bool connect(const char* ipAddress, int port);
    bool sendAudioPacket(const std::vector<int16_t>& audioData);
    
    void disconnect();
    bool isActive() const;
};

#endif