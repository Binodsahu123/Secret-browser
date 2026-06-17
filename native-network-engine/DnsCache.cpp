#include "DnsCache.h"

DnsCache& DnsCache::getInstance() {
    static DnsCache instance;
    return instance;
}

void DnsCache::put(const std::string& host, const std::string& ip) {
    std::lock_guard<std::mutex> lock(mtx);
    cacheMap[host] = ip;
}

std::string DnsCache::get(const std::string& host) {
    std::lock_guard<std::mutex> lock(mtx);
    auto it = cacheMap.find(host);
    if (it != cacheMap.end()) {
        return it->second;
    }
    return "";
}
