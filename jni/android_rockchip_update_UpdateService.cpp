#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#define LOG_TAG __FILE__
#include <utils/Log.h> 

static int read_file(const char* path, int offset, int len, char* buf)
{
    int result, ret;
    int fd = open(path, O_RDWR);
    if (fd < 0) {
        LOGE("Fail to open image file file '%s', error : '%s'.",
             path, strerror(errno));
        return fd;
    }
    if (lseek(fd, offset, SEEK_SET) < 0) {
        LOGE("Fail to seek image file file '%s', error : '%s'.",
             path, strerror(errno));
        result = -1;
        goto clean_and_return;
    }
    ret = read(fd, buf, len);
    if (ret < 0) {
        LOGE("Fail to read image file file '%s', bytes read : '%d', error : '%s'.",
             path, ret, strerror(errno));
        result = errno;
        goto clean_and_return;
    }
    result = 0;
clean_and_return:
    if (fd) close(fd);
    return result;
}

static jstring getImageProductName(JNIEnv* env, jobject object, jstring j_path)
{
    jstring result = NULL;
    const char* path = env->GetStringUTFChars(j_path, 0);
    if (!path) {
        LOGE("Failed to get utf-8 path from 'j_path'.");
        return NULL;
    }
    LOGD("Image file path : '%s'.", path);
    char buf[64];
    int offset = 0;
    int ret = read_file(path, offset, sizeof(buf), buf);
    if (ret) {
        LOGE("Fail to read image file file '%s', error : '%s'.",
             path, strerror(errno));
        goto clean_and_return;
    }
    if (*(unsigned*)(buf)==0x57464B52)
        offset = *(unsigned*)(buf+0x21);
    ret = read_file(path, offset+8, sizeof(buf), buf);
    if (ret) {
        LOGE("Fail to read image file file '%s', error : '%s'.",
             path, strerror(errno));
        goto clean_and_return;
    }
    ret = strlen(buf);
    if (ret >= 64) {
        LOGE("Read invalid (too long) name info(length : '%d'). "
             "Image file must be invalid!", ret);
        goto clean_and_return;
    }
    LOGD("Porduce name : '%s'.", buf);
    result = env->NewStringUTF(buf);
clean_and_return:
    env->ReleaseStringUTFChars(j_path, path);
    return result;
}

static jstring getImageVersion(JNIEnv* env, jobject object, jstring j_path)
{
    jstring result = NULL;
    const char* path = env->GetStringUTFChars(j_path, 0);
    if (!path) {
        LOGE("Failed to get utf-8 path from 'j_path'.");
        return NULL;
    }
    LOGD("Image file path : '%s'.", path);
    char buf[64];
    int offset = 0;
    int ret = read_file(path, offset, sizeof(buf), buf);
    if (ret) {
        LOGE("Fail to read image file file '%s', error : '%s'.",
             path, strerror(errno));
        goto clean_and_return;
    }
    if (*(unsigned*)(buf)==0x57464B52)
        offset = *(unsigned*)(buf+0x21);
    ret = read_file(path, offset+0x84, 4, buf);
    if (ret) {
        LOGE("Fail to read image file file '%s', error : '%s'.",
             path, strerror(errno));
        goto clean_and_return;
    }
    sprintf(buf, "%d.%d.%d", buf[3], buf[2], buf[0] + (buf[1]<<8));
    LOGD("Image version : '%s'.", buf);
    result = env->NewStringUTF(buf);
clean_and_return:
    env->ReleaseStringUTFChars(j_path, path);
    return result;
}

static JNINativeMethod gMethods[] = {
    { "getImageVersion",     "(Ljava/lang/String;)Ljava/lang/String;", (void*)getImageVersion},
    { "getImageProductName", "(Ljava/lang/String;)Ljava/lang/String;", (void*)getImageProductName }
};
#define gMethodsCount (sizeof(gMethods) / sizeof(gMethods[0]))

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
    const char* name = "android/rockchip/update/service/RKUpdateService";
    JNIEnv* env = NULL;
    jclass gClass;
    LOGI("JNI_OnLoad");
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_4) != JNI_OK) {
        LOGE( "ERROR: GetEnv failed");
        return -1;
    }
    if ((gClass = env->FindClass(name)) == NULL) {
        LOGE("Native registration unable to find class '%s'", name);
        goto clean_and_return;
    }
    if (env->RegisterNatives(gClass, gMethods, gMethodsCount) < 0) {
        fprintf(stderr, "RegisterNatives failed for '%s'", name);
        goto clean_and_return;
    }
    return JNI_VERSION_1_4;
clean_and_return:
    LOGE("ERROR: registerNatives failed");
    return -1;
}
