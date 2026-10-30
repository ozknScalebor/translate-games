// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
// Adapted from chiaki-ng (android/app/src/main/cpp/chiaki-jni.h).

#ifndef ALTYAZI_JNI_COMMON_H
#define ALTYAZI_JNI_COMMON_H

#include <jni.h>
#include <stdbool.h>

#define JNI_VERSION JNI_VERSION_1_6

#define BASE_PACKAGE "com/ozkan/altyazi/remote/chiaki"
#define JNI_FCN(name) Java_com_ozkan_altyazi_remote_chiaki_ChiakiNative_##name

#define E (*env)

/** Returns a JNIEnv for the calling thread; *attached is set when the thread had to be attached. */
JNIEnv *attach_thread_jni(bool *attached);
/** Detaches the calling thread again if attach_thread_jni() attached it. */
void detach_thread_jni(bool attached);
jobject jnistr_from_ascii(JNIEnv *env, const char *str);

extern JavaVM *global_vm;

#endif
