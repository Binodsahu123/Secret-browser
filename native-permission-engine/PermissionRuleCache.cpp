#include <string>
#include <unordered_map>
#include <mutex>

class PermissionRuleCache {
private:
    std::unordered_map<std::string, std::unordered_map<std::string, std::string>> cache;
    std::mutex mtx;

public:
    void put(const std::string& origin, const std::string& type, const std::string& decision) {
        std::lock_guard<std::mutex> lock(mtx);
        cache[origin][type] = decision;
    }

    std::string get(const std::string& origin, const std::string& type) {
        std::lock_guard<std::mutex> lock(mtx);
        auto it = cache.find(origin);
        if (it != cache.end()) {
            auto inner_it = it->second.find(type);
            if (inner_it != it->second.end()) {
                return inner_it->second;
            }
        }
        return "";
    }

    void clear() {
        std::lock_guard<std::mutex> lock(mtx);
        cache.clear();
    }
};
