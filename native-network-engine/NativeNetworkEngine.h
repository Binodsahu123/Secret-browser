#ifndef NATIVE_NETWORK_ENGINE_H
#define NATIVE_NETWORK_ENGINE_H

#include <jni.h>
#include <string>

extern "C" {
    JNIEXPORT jstring JNICALL Java_com_example_nativenetworkengine_NativeNetworkEngine_nativeParseRequestHeaders(
        JNIEnv* env, jclass clazz, jstring rawHeadersStr
    );

    JNIEXPORT jstring JNICALL Java_com_example_nativenetworkengine_NativeNetworkEngine_nativeAnalyzeResponseBody(
        JNIEnv* env, jclass clazz, jstring bodyStr, jstring mimeTypeStr
    );

    JNIEXPORT jstring JNICALL Java_com_example_nativenetworkengine_NativeNetworkEngine_nativeTrackTraffic(
        JNIEnv* env, jclass clazz, jstring hostStr, jlong bytesTransferred
    );
}

#endif // NATIVE_NETWORK_ENGINE_H
