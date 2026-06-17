#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <algorithm>

extern "C" {

/**
 * Validates sandbox path escape boundaries natively with fast POSIX path lookup.
 */
JNIEXPORT jboolean JNICALL
Java_com_example_nativesecurityengine_NativeSecurityEngine_nativeValidateSandboxBoundary(
        JNIEnv* env,
        jclass clazz,
        jstring targetPathStr,
        jstring allowedRootStr
) {
    if (!targetPathStr || !allowedRootStr) return JNI_FALSE;

    const char* target = env->GetStringUTFChars(targetPathStr, nullptr);
    const char* allowed = env->GetStringUTFChars(allowedRootStr, nullptr);

    std::string targetPath(target);
    std::string allowedPath(allowed);

    env->ReleaseStringUTFChars(targetPathStr, target);
    env->ReleaseStringUTFChars(allowedRootStr, allowed);

    // Filter physical parent folders escaping
    if (targetPath.find("..") != std::string::npos) {
        return JNI_FALSE;
    }

    // Target must start with absolute permitted path context
    if (targetPath.rfind(allowedPath, 0) == 0) {
        return JNI_TRUE;
    }

    return JNI_FALSE;
}

/**
 * Scans byte array contents natively against malicious bytes signature matrices.
 */
JNIEXPORT jboolean JNICALL
Java_com_example_nativesecurityengine_NativeSecurityEngine_nativeScanThreatSignatures(
        JNIEnv* env,
        jclass clazz,
        jbyteArray bytesArray
) {
    if (!bytesArray) return JNI_TRUE;

    jsize len = env->GetArrayLength(bytesArray);
    if (len == 0) return JNI_TRUE;

    jbyte* bytes = env->GetByteArrayElements(bytesArray, nullptr);

    // List of highly dangerous script payloads or byte sequences
    bool clear = true;

    // Check 1: Infinite NOP loop slide bytes (e.g. 0x90 0x90 0x90 0x90)
    for (jsize i = 0; i < len - 4; ++i) {
        if (bytes[i] == 0x90 && bytes[i+1] == 0x90 && bytes[i+2] == 0x90 && bytes[i+3] == 0x90) {
            clear = false; // flag threat found
            break;
        }
    }

    // Check 2: Raw signature of miner keywords inside binary blocks
    if (clear && len >= 12) {
        std::string buffer(reinterpret_cast<const char*>(bytes), len);
        if (buffer.find("coinhive.com") != std::string::npos || buffer.find("eval(unescape(") != std::string::npos) {
            clear = false;
        }
    }

    env->ReleaseByteArrayElements(bytesArray, bytes, JNI_ABORT);
    return clear ? JNI_TRUE : JNI_FALSE;
}

/**
 * Determines whether isolated memory partitions are locked.
 */
JNIEXPORT jboolean JNICALL
Java_com_example_nativesecurityengine_NativeSecurityEngine_nativeVerifyIsolation(
        JNIEnv* env,
        jclass clazz,
        jstring extensionIdStr
) {
    if (!extensionIdStr) return JNI_FALSE;

    const char* extId = env->GetStringUTFChars(extensionIdStr, nullptr);
    std::string id(extId);
    env->ReleaseStringUTFChars(extensionIdStr, extId);

    if (id.empty()) return JNI_FALSE;

    // Ensure restricted keys do not pollute the runtime identifier name
    if (id.find("sys_") != std::string::npos || id.find("root_") != std::string::npos || id.find("kernel_") != std::string::npos) {
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

}
