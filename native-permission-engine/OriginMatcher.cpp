#include <string>

bool matchOriginPattern(const std::string& origin, const std::string& pattern) {
    if (pattern == "*") return true;
    if (origin == pattern) return true;
    if (pattern.rfind("*.", 0) == 0) {
        std::string suffix = pattern.substr(2);
        if (origin.size() >= suffix.size()) {
            return origin.compare(origin.size() - suffix.size(), suffix.size(), suffix) == 0;
        }
    }
    return false;
}
