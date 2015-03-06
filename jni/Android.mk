LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := rockchip_update_jni
LOCAL_MODULE_TAGS := optional
LOCAL_PRELINK_MODULE := false
LOCAL_SRC_FILES := android_rockchip_update_UpdateService.cpp
LOCAL_C_INCLUDES += $(JNI_H_INCLUDE)
LOCAL_SHARED_LIBRARIES := liblog libutils

include $(BUILD_SHARED_LIBRARY)
