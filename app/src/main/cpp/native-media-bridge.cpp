#include <jni.h>
#include <android/log.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <string>
#include <vector>

#define LOG_TAG "NativeMediaBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global OpenSL ES hardware references
SLObjectItf engineObject = nullptr;
SLEngineItf engineInstance = nullptr;
SLObjectItf recorderObject = nullptr;
SLRecordItf recorderInstance = nullptr;
SLAndroidSimpleBufferQueueItf bufferQueueInstance = nullptr;

#define BUFFER_SIZE_SAMPLES 1024
int8_t activePcmBuffer[BUFFER_SIZE_SAMPLES * 2]; // 16-bit PCM

// Asynchronous Queue callback triggered when a PCM block is filled by the hardware
void bqRecorderCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
    // Read captured raw PCM blocks from hardware
    // In a full implementation, block buffers are written directly to internal server or returned to Java wrapper.
    SLresult result = (*bufferQueueInstance)->Enqueue(bufferQueueInstance, activePcmBuffer, sizeof(activePcmBuffer));
    if (result != SL_RESULT_SUCCESS) {
        LOGE("Native Recorder callback enqueue failure: %d", result);
    }
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_browser_engine_NativeAudioEngine_startNativeCapture(JNIEnv* env, jobject obj) {
    LOGI("Initializing high-performance Native OpenSL ES Audio recorder...");

    SLresult result;

    // 1. Create Engine Object
    result = slCreateEngine(&engineObject, 0, nullptr, 0, nullptr, nullptr);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("slCreateEngine Failed");
        return JNI_FALSE;
    }

    // Realize Engine
    result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("Engine Realize Failed");
        return JNI_FALSE;
    }

    // Get the Interface
    result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineInstance);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("GetInterface (Engine) Failed");
        return JNI_FALSE;
    }

    // 2. Configure Audio input source
    SLDataLocator_AndroidSimpleBufferQueue loc_bq = {
        SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
        2
    };
    SLDataFormat_PCM format_pcm = {
        SL_DATAFORMAT_PCM,
        1, // Mono
        SL_SAMPLINGRATE_16, // 16000Hz
        SL_PCMSAMPLEFORMAT_FIXED_16,
        SL_PCMSAMPLEFORMAT_FIXED_16,
        SL_SPEAKER_FRONT_CENTER,
        SL_BYTEORDER_LITTLEENDIAN
    };
    SLDataSource audioSrc = {&loc_bq, &format_pcm};

    // Configure Audio sink connection
    SLDataLocator_IODevice loc_dev = {
        SL_DATALOCATOR_IODEVICE,
        SL_IODEVICE_AUDIOINPUT,
        SL_DEFAULTDEVICEID_AUDIOINPUT,
        nullptr
    };
    SLDataSink audioSnk = {&loc_dev, nullptr};

    // 3. Create Audio Recorder Object
    const SLInterfaceID ids[2] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE, SL_IID_RECORD};
    const SLboolean req[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};

    result = (*engineInstance)->CreateAudioRecorder(engineInstance, &recorderObject, &audioSrc, &audioSnk, 2, ids, req);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("CreateAudioRecorder Failed");
        return JNI_FALSE;
    }

    // Realize Recorder
    result = (*recorderObject)->Realize(recorderObject, SL_BOOLEAN_FALSE);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("Recorder Realize Failed");
        return JNI_FALSE;
    }

    // Get classes and interfaces
    result = (*recorderObject)->GetInterface(recorderObject, SL_IID_RECORD, &recorderInstance);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("GetInterface (Record) Failed");
        return JNI_FALSE;
    }

    result = (*recorderObject)->GetInterface(recorderObject, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &bufferQueueInstance);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("GetInterface (Buffer Queue) Failed");
        return JNI_FALSE;
    }

    // Set callback
    result = (*bufferQueueInstance)->RegisterCallback(bufferQueueInstance, bqRecorderCallback, nullptr);
    if (result != SL_RESULT_SUCCESS) {
        LOGE("RegisterCallback (Recorder) Failed");
        return JNI_FALSE;
    }

    // Set record state active
    result = (*recorderInstance)->SetRecordState(recorderInstance, SL_RECORDSTATE_RECORDING);
    if (result != SL_RESULT_SUCCESS) {
         LOGE("SetRecordState Failed");
         return JNI_FALSE;
    }

    // Begin capturing loop
    (*bufferQueueInstance)->Enqueue(bufferQueueInstance, activePcmBuffer, sizeof(activePcmBuffer));

    LOGI("Native Audio capture engine successfully started!");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_browser_engine_NativeAudioEngine_stopNativeCapture(JNIEnv* env, jobject obj) {
    LOGI("Stopping Native Audio capture engine...");
    if (recorderInstance != nullptr) {
        (*recorderInstance)->SetRecordState(recorderInstance, SL_RECORDSTATE_STOPPED);
    }
    if (recorderObject != nullptr) {
        (*recorderObject)->Destroy(recorderObject);
        recorderObject = nullptr;
        recorderInstance = nullptr;
    }
    if (engineObject != nullptr) {
        (*engineObject)->Destroy(engineObject);
        engineObject = nullptr;
        engineInstance = nullptr;
    }
    LOGI("Native Audio capture engine stopped and hardware released.");
}

}
