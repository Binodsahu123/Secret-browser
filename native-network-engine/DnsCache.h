#ifndef DNS_CACHE_H
#define DNS_CACHE_H

#include <string>
#include <unordered_map>
#include <vector>
#include <mutex>

class DnsCache {
public:
    static DnsCache& getInstance();
    void put(const std::string& host, const std::string& ip);
    std::string get(const std::string& host);

private:
    DnsCache() = default;
    std::unordered_map<std::string, std::string> cacheMap;
    std::mutex mtx;
};

#endif // DNS_CACHE_H
