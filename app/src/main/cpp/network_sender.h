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
    int serverSocket = -1;  // Socket buat nunggu tamu
    int clientSocket = -1;  // Socket buat ngobrol sama PC
    struct sockaddr_in address;
    
    std::atomic<bool> isConnected{false};
    std::atomic<uint64_t> bytesSent{0};

public:
    NetworkSender();
    ~NetworkSender();

    // Setup Server di Port tertentu
    bool startServer(int port);
    
    // Tunggu PC connect (Blocking) - Dipanggil di thread terpisah
    bool waitForConnection();
    
    bool sendAudioPacket(const std::vector<int16_t>& audioData);
    
    void closeClient(); // Putus koneksi PC (tapi server tetep jalan)
    void stop();        // Matikan total
    
    bool isActive() const;
};

#endif