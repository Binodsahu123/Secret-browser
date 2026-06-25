#include <string>
#include <algorithm>

std::string normalizeDomainString(std::string domain) {
    std::transform(domain.begin(), domain.end(), domain.begin(), ::tolower);
    if (domain.rfind("https://", 0) == 0) {
        domain = domain.substr(8);
    } else if (domain.rfind("http://", 0) == 0) {
        domain = domain.substr(7);
    }
    size_t slash = domain.find('/');
    if (slash != std::string::npos) {
        domain = domain.substr(0, slash);
    }
    return domain;
}
