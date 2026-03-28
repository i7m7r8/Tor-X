#include <jni.h>
#include <string>
#include <android/log.h>

#define TAG "TOR-X-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {
    JNIEXPORT jlong JNICALL Java_com_torx_core_VPNCore_initNative(
        JNIEnv* env, jobject obj) {
        LOGI("JNI Native initialization");
        return 1;
    }

    JNIEXPORT jint JNICALL Java_com_torx_core_VPNCore_connect(
        JNIEnv* env, jobject obj, jlong handle) {
        LOGI("JNI Connect called");
        return 1;
    }

    JNIEXPORT jint JNICALL Java_com_torx_core_VPNCore_disconnect(
        JNIEnv* env, jobject obj, jlong handle) {
        LOGI("JNI Disconnect called");
        return 1;
    }

    JNIEXPORT jboolean JNICALL Java_com_torx_core_VPNCore_isConnected(
        JNIEnv* env, jobject obj, jlong handle) {
        LOGI("JNI isConnected called");
        return JNI_TRUE;
    }
}
