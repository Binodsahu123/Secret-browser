#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "NativeUrlRewriteEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_example_browser_NativeDesktopEngine_nativeRewriteUrl(JNIEnv* env, jobject obj, jstring url, jboolean toDesktop) {
    if (!url) return nullptr;
    const char* urlStr = env->GetStringUTFChars(url, nullptr);
    std::string sUrl(urlStr);
    env->ReleaseStringUTFChars(url, urlStr);

    std::string result = sUrl;

    if (toDesktop) {
        // Rewrite rules for desktop rendering
        size_t mPos = sUrl.find("://m.youtube.com");
        if (mPos != std::string::npos) {
            result.replace(mPos + 3, 2, "www");
        } else {
            size_t mFb = sUrl.find("://m.facebook.com");
            if (mFb != std::string::npos) {
                result.replace(mFb + 3, 2, "www");
            } else {
                size_t mReddit = sUrl.find("://m.reddit.com");
                if (mReddit != std::string::npos) {
                    result.replace(mReddit + 3, 2, "www");
                } else {
                    size_t mX = sUrl.find("://m.x.com");
                    if (mX != std::string::npos) {
                        result.replace(mX + 3, 2, "");
                    }
                }
            }
        }
    } else {
        // Rewrite rules for mobile fallback
        size_t wwwPos = sUrl.find("://www.youtube.com");
        if (wwwPos != std::string::npos) {
            result.replace(wwwPos + 3, 3, "m");
        } else {
            size_t wwwFb = sUrl.find("://www.facebook.com");
            if (wwwFb != std::string::npos) {
                result.replace(wwwFb + 3, 3, "m");
            } else {
                size_t wwwReddit = sUrl.find("://www.reddit.com");
                if (wwwReddit != std::string::npos) {
                    result.replace(wwwReddit + 3, 3, "m");
                }
            }
        }
    }

    return env->NewStringUTF(result.c_str());
}

}
