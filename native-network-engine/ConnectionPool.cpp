#include "ConnectionPool.h"

ConnectionPool& ConnectionPool::getInstance() {
    static ConnectionPool instance;
    return instance;
}

void ConnectionPool::incrementCount(const std::string& host) {
    std::lock_guard<std::mutex> lock(mtx);
    poolMap[host]++;
}

int ConnectionPool::getCount(const std::string& host) {
    std::lock_guard<std::mutex> lock(mtx);
    auto it = poolMap.find(host);
    if (it != poolMap.end()) {
        return it->second;
    }
    return 0;
}
