#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <android/log.h>
#include <atomic>
#include <thread>
#include <vector>
#include <netinet/tcp.h> 
#include <netinet/in.h>

#define LOG_TAG "NetworkSender"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class NetworkSender {
private:
    int sockfd = -1;
    struct sockaddr_in serverAddr;
    std::atomic<bool> isConnected{false};
    
    // Stats untuk monitoring
    std::atomic<uint64_t> bytesSent{0};
    std::atomic<uint32_t> packetsSent{0};
    std::atomic<uint32_t> errors{0};

public:
    // Initialize socket dan connect ke PC
    bool connect(const char* ipAddress, int port) {
        // Create UDP socket untuk low latency
        sockfd = socket(AF_INET, SOCK_DGRAM, 0);
        if (sockfd < 0) {
            LOGE("Failed to create socket: %s", strerror(errno));
            return false;
        }
        
        // Set socket options untuk optimasi
        int optval = 1;
        setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, &optval, sizeof(optval));
        
        // Increase send buffer size
        int sendbuf = 256 * 1024; // 256KB
        setsockopt(sockfd, SOL_SOCKET, SO_SNDBUF, &sendbuf, sizeof(sendbuf));
        
        // Configure server address
        memset(&serverAddr, 0, sizeof(serverAddr));
        serverAddr.sin_family = AF_INET;
        serverAddr.sin_port = htons(port);
        
        if (inet_pton(AF_INET, ipAddress, &serverAddr.sin_addr) <= 0) {
            LOGE("Invalid IP address: %s", ipAddress);
            close(sockfd);
            sockfd = -1;
            return false;
        }
        
        isConnected = true;
        LOGI("Connected to %s:%d", ipAddress, port);
        return true;
    }
    
    // Send audio packet dengan header minimal
    bool sendAudioPacket(const std::vector<int16_t>& audioData) {
        if (!isConnected || sockfd < 0) {
            return false;
        }
        
        // Simple packet format:
        // [4 bytes: packet size] [4 bytes: timestamp] [audio data]
        uint32_t dataSize = audioData.size() * sizeof(int16_t);
        uint32_t timestamp = static_cast<uint32_t>(
            std::chrono::steady_clock::now().time_since_epoch().count()
        );
        
        std::vector<uint8_t> packet(8 + dataSize);
        
        // Write header
        memcpy(packet.data(), &dataSize, 4);
        memcpy(packet.data() + 4, &timestamp, 4);
        
        // Write audio data
        memcpy(packet.data() + 8, audioData.data(), dataSize);
        
        // Send packet
        ssize_t sent = sendto(sockfd, packet.data(), packet.size(), 0,
                             (struct sockaddr*)&serverAddr, sizeof(serverAddr));
        
        if (sent < 0) {
            LOGE("Send failed: %s", strerror(errno));
            errors++;
            return false;
        }
        
        // Update stats
        bytesSent += sent;
        packetsSent++;
        
        // Log stats setiap 1000 packets
        if (packetsSent % 1000 == 0) {
            LOGI("Stats: %llu bytes, %u packets, %u errors",
                 (unsigned long long)bytesSent.load(),
                 packetsSent.load(),
                 errors.load());
        }
        
        return true;
    }
    
    // Alternative: TCP dengan buffering untuk reliability
    bool connectTCP(const char* ipAddress, int port) {
        sockfd = socket(AF_INET, SOCK_STREAM, 0);
        if (sockfd < 0) {
            LOGE("Failed to create TCP socket: %s", strerror(errno));
            return false;
        }
        
        // Disable Nagle's algorithm untuk lower latency
        int flag = 1;
        setsockopt(sockfd, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag));
        
        // Set keepalive
        int keepalive = 1;
        setsockopt(sockfd, SOL_SOCKET, SO_KEEPALIVE, &keepalive, sizeof(keepalive));
        
        memset(&serverAddr, 0, sizeof(serverAddr));
        serverAddr.sin_family = AF_INET;
        serverAddr.sin_port = htons(port);
        inet_pton(AF_INET, ipAddress, &serverAddr.sin_addr);
        
        if (::connect(sockfd, (struct sockaddr*)&serverAddr, sizeof(serverAddr)) < 0) {
            LOGE("TCP connect failed: %s", strerror(errno));
            close(sockfd);
            sockfd = -1;
            return false;
        }
        
        isConnected = true;
        LOGI("TCP connected to %s:%d", ipAddress, port);
        return true;
    }
    
    bool sendTCP(const std::vector<int16_t>& audioData) {
        if (!isConnected || sockfd < 0) return false;
        
        uint32_t dataSize = audioData.size() * sizeof(int16_t);
        
        // Send size first
        if (send(sockfd, &dataSize, sizeof(dataSize), 0) < 0) {
            LOGE("TCP send size failed: %s", strerror(errno));
            return false;
        }
        
        // Send audio data
        ssize_t sent = send(sockfd, audioData.data(), dataSize, 0);
        if (sent < 0) {
            LOGE("TCP send data failed: %s", strerror(errno));
            return false;
        }
        
        bytesSent += sent + sizeof(dataSize);
        packetsSent++;
        return true;
    }
    
    void disconnect() {
        if (sockfd >= 0) {
            close(sockfd);
            sockfd = -1;
        }
        isConnected = false;
        LOGI("Disconnected. Final stats: %llu bytes, %u packets",
             (unsigned long long)bytesSent.load(),
             packetsSent.load());
    }
    
    bool isActive() const {
        return isConnected.load();
    }
    
    ~NetworkSender() {
        disconnect();
    }
};
