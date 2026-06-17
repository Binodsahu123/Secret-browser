#ifndef NATIVE_DOWNLOAD_ENGINE_H
#define NATIVE_DOWNLOAD_ENGINE_H

#include <jni.h>
#include <string>

extern "C" {
    JNIEXPORT jstring JNICALL Java_com_example_nativedownloadengine_NativeDownloadEngine_nativeCalculateChunks(
        JNIEnv* env, jclass clazz, jlong fileSize, jint numThreads
    );

    JNIEXPORT jboolean JNICALL Java_com_example_nativedownloadengine_NativeDownloadEngine_nativeVerifyFileIntegrity(
        JNIEnv* env, jclass clazz, jstring filePathStr, jstring expectedSHA256Str
    );

    JNIEXPORT jboolean JNICALL Java_com_example_nativedownloadengine_NativeDownloadEngine_nativeAssembleChunks(
        JNIEnv* env, jclass clazz, jobjectArray chunkPathsArray, jstring outputPathStr
    );
}

#endif // NATIVE_DOWNLOAD_ENGINE_H
