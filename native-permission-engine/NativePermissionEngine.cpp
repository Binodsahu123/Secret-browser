#include <jni.h>
#include <string>
#include <algorithm>
#include <unordered_map>
#include <mutex>

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_permissionengine_PermissionNativeBridge_nativeMatchOrigin(
    JNIEnv* env, jobject thiz, jstring j_origin, jstring j_pattern) {
    
    if (!j_origin || !j_pattern) return JNI_FALSE;
    
    const char* origin_str = env->GetStringUTFChars(j_origin, nullptr);
    const char* pattern_str = env->GetStringUTFChars(j_pattern, nullptr);
    
    std::string origin(origin_str);
    std::string pattern(pattern_str);
    
    env->ReleaseStringUTFChars(j_origin, origin_str);
    env->ReleaseStringUTFChars(j_pattern, pattern_str);
    
    if (pattern == "*") return JNI_TRUE;
    if (origin == pattern) return JNI_TRUE;
    
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_permissionengine_PermissionNativeBridge_nativeNormalizeDomain(
    JNIEnv* env, jobject thiz, jstring j_domain) {
    
    if (!j_domain) return nullptr;
    
    const char* domain_str = env->GetStringUTFChars(j_domain, nullptr);
    std::string domain(domain_str);
    env->ReleaseStringUTFChars(j_domain, domain_str);
    
    // Normalize lower
    std::transform(domain.begin(), domain.end(), domain.begin(), ::tolower);
    
    // Simple strips
    if (domain.rfind("https://", 0) == 0) {
        domain = domain.substr(8);
    } else if (domain.rfind("http://", 0) == 0) {
        domain = domain.substr(7);
    }
    
    size_t slash = domain.find('/');
    if (slash != std::string::npos) {
        domain = domain.substr(0, slash);
    }
    
    return env->NewStringUTF(domain.c_str());
}

static std::unordered_map<std::string, std::unordered_map<std::string, std::string>> g_permission_cache;
static std::mutex g_cache_mutex;

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_permissionengine_PermissionNativeBridge_nativePermissionLookup(
    JNIEnv* env, jobject thiz, jstring j_origin, jstring j_permission_type) {
    
    if (!j_origin || !j_permission_type) return nullptr;
    
    const char* origin_str = env->GetStringUTFChars(j_origin, nullptr);
    const char* perm_str = env->GetStringUTFChars(j_permission_type, nullptr);
    
    std::string origin(origin_str);
    std::string perm(perm_str);
    
    env->ReleaseStringUTFChars(j_origin, origin_str);
    env->ReleaseStringUTFChars(j_permission_type, perm_str);
    
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    auto it = g_permission_cache.find(origin);
    if (it != g_permission_cache.end()) {
        auto inner_it = it->second.find(perm);
        if (inner_it != it->second.end()) {
            return env->NewStringUTF(inner_it->second.c_str());
        }
    }
    
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_permissionengine_PermissionNativeBridge_nativePermissionCache(
    JNIEnv* env, jobject thiz, jstring j_origin, jstring j_permission_type, jstring j_state) {
    
    if (!j_origin || !j_permission_type || !j_state) return;
    
    const char* origin_str = env->GetStringUTFChars(j_origin, nullptr);
    const char* perm_str = env->GetStringUTFChars(j_permission_type, nullptr);
    const char* state_str = env->GetStringUTFChars(j_state, nullptr);
    
    std::string origin(origin_str);
    std::string perm(perm_str);
    std::string state(state_str);
    
    env->ReleaseStringUTFChars(j_origin, origin_str);
    env->ReleaseStringUTFChars(j_permission_type, perm_str);
    env->ReleaseStringUTFChars(j_state, state_str);
    
    std::lock_guard<std::mutex> lock(g_cache_mutex);
    g_permission_cache[origin][perm] = state;
}
