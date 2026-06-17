#ifndef CONNECTION_POOL_H
#define CONNECTION_POOL_H

#include <string>
#include <unordered_map>
#include <mutex>

class ConnectionPool {
public:
    static ConnectionPool& getInstance();
    void incrementCount(const std::string& host);
    int getCount(const std::string& host);

private:
    ConnectionPool() = default;
    std::unordered_map<std::string, int> poolMap;
    std::mutex mtx;
};

#endif // CONNECTION_POOL_H
