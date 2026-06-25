#include <string>

int getPermissionScore(const std::string& permission_type) {
    if (permission_type == "MICROPHONE" || permission_type == "CAMERA") {
        return 90; // High risk hardware resources
    } else if (permission_type == "LOCATION") {
        return 80; // High risk user privacy resources
    } else if (permission_type == "NOTIFICATIONS") {
        return 50; // Medium risk resource
    }
    return 10; // Low risk
}
