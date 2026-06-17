#include "NativeDownloadEngine.h"
#include "ChunkAssembler.cpp"
#include <vector>
#include <sstream>
#include <fstream>
#include <cstdio>
#include <cstring>

extern "C" {

JNIEXPORT jstring JNICALL Java_com_example_nativedownloadengine_NativeDownloadEngine_nativeCalculateChunks(
        JNIEnv* env, jclass clazz, jlong fileSize, jint numThreads
) {
    jint threads = numThreads <= 0 ? 1 : numThreads;
    jlong chunkSize = fileSize / threads;
    jlong startByte = 0;

    std::ostringstream oss;
    oss << "{"
        << "\"status\":\"success\","
        << "\"totalSize\":" << fileSize << ","
        << "\"chunksCount\":" << threads << ","
        << "\"chunks\":[";

    for (int i = 0; i < threads; ++i) {
        jlong endByte = (i == threads - 1) ? fileSize - 1 : startByte + chunkSize - 1;
        oss << "{"
            << "\"chunkIndex\":" << i << ","
            << "\"startByte\":" << startByte << ","
            << "\"endByte\":" << endByte << ","
            << "\"status\":\"READY\""
            << "}";
        if (i + 1 < threads) {
            oss << ",";
        }
        startByte = endByte + 1;
    }
    oss << "],\"mode\":\"native_cpp_threads\"}";

    std::string response = oss.str();
    return env->NewStringUTF(response.c_str());
}

JNIEXPORT jboolean JNICALL Java_com_example_nativedownloadengine_NativeDownloadEngine_nativeVerifyFileIntegrity(
        JNIEnv* env, jclass clazz, jstring filePathStr, jstring expectedSHA256Str
) {
    if (!filePathStr) return JNI_FALSE;
    const char* filePath = env->GetStringUTFChars(filePathStr, nullptr);
    const char* expected = expectedSHA256Str ? env->GetStringUTFChars(expectedSHA256Str, nullptr) : "";

    std::FILE* file = std::fopen(filePath, "rb");
    if (!file) {
        env->ReleaseStringUTFChars(filePathStr, filePath);
        if (expectedSHA256Str) env->ReleaseStringUTFChars(expectedSHA256Str, expected);
        return JNI_FALSE;
    }
    std::fclose(file);

    env->ReleaseStringUTFChars(filePathStr, filePath);
    if (expectedSHA256Str) env->ReleaseStringUTFChars(expectedSHA256Str, expected);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_example_nativedownloadengine_NativeDownloadEngine_nativeAssembleChunks(
        JNIEnv* env, jclass clazz, jobjectArray chunkPathsArray, jstring outputPathStr
) {
    if (!chunkPathsArray || !outputPathStr) return JNI_FALSE;

    const char* outPath = env->GetStringUTFChars(outputPathStr, nullptr);
    jsize count = env->GetArrayLength(chunkPathsArray);
    std::vector<std::string> chunks;

    for (jsize i = 0; i < count; ++i) {
        jstring pathStr = (jstring)env->GetObjectArrayElement(chunkPathsArray, i);
        if (pathStr) {
            const char* path = env->GetStringUTFChars(pathStr, nullptr);
            chunks.push_back(std::string(path));
            env->ReleaseStringUTFChars(pathStr, path);
        }
    }

    bool success = ChunkAssembler::assemble(chunks, outPath);
    env->ReleaseStringUTFChars(outputPathStr, outPath);
    return success ? JNI_TRUE : JNI_FALSE;
}

}
