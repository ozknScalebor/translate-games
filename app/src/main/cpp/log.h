// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
// Adapted from chiaki-ng (android/app/src/main/cpp/log.h).

#ifndef ALTYAZI_JNI_LOG_H
#define ALTYAZI_JNI_LOG_H

#include <chiaki/log.h>

#include <jni.h>

typedef struct android_jni_chiaki_log_t
{
	jobject java_log;
	jmethodID java_log_meth;
	ChiakiLog log;
} AndroidChiakiJNILog;

/** Logs to logcat only. */
void android_chiaki_logcat_log_init(ChiakiLog *log, uint32_t level);

/** Logs to logcat and forwards every line to a Kotlin ChiakiLog object. */
void android_chiaki_jni_log_init(AndroidChiakiJNILog *log, JNIEnv *env, jobject java_log);
void android_chiaki_jni_log_fini(AndroidChiakiJNILog *log, JNIEnv *env);

#endif
