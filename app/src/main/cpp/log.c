// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
// Adapted from chiaki-ng (android/app/src/main/cpp/log.c).

#include "log.h"
#include "jni-common.h"

#include <android/log.h>

#define LOG_TAG "RemotePlay"

static void log_cb_logcat(ChiakiLogLevel level, const char *msg, void *user)
{
	int prio;
	switch(level)
	{
		case CHIAKI_LOG_DEBUG: prio = ANDROID_LOG_DEBUG; break;
		case CHIAKI_LOG_VERBOSE: prio = ANDROID_LOG_VERBOSE; break;
		case CHIAKI_LOG_INFO: prio = ANDROID_LOG_INFO; break;
		case CHIAKI_LOG_WARNING: prio = ANDROID_LOG_WARN; break;
		case CHIAKI_LOG_ERROR: prio = ANDROID_LOG_ERROR; break;
		default: prio = ANDROID_LOG_INFO; break;
	}
	__android_log_write(prio, LOG_TAG, msg);
}

void android_chiaki_logcat_log_init(ChiakiLog *log, uint32_t level)
{
	chiaki_log_init(log, level, log_cb_logcat, NULL);
}

static void log_cb_jni(ChiakiLogLevel level, const char *msg, void *user)
{
	log_cb_logcat(level, msg, NULL);

	AndroidChiakiJNILog *log = user;
	bool attached = false;
	JNIEnv *env = attach_thread_jni(&attached);
	if(!env)
		return;
	jobject text = jnistr_from_ascii(env, msg);
	E->CallVoidMethod(env, log->java_log, log->java_log_meth, (jint)level, text);
	if(text)
		E->DeleteLocalRef(env, text);
	detach_thread_jni(attached);
}

void android_chiaki_jni_log_init(AndroidChiakiJNILog *log, JNIEnv *env, jobject java_log)
{
	log->java_log = E->NewGlobalRef(env, java_log);
	jclass log_class = E->GetObjectClass(env, log->java_log);
	log->java_log_meth = E->GetMethodID(env, log_class, "log", "(ILjava/lang/String;)V");
	log->log.level_mask = (uint32_t)E->GetIntField(env, log->java_log, E->GetFieldID(env, log_class, "levelMask", "I"));
	log->log.cb = log_cb_jni;
	log->log.user = log;
}

void android_chiaki_jni_log_fini(AndroidChiakiJNILog *log, JNIEnv *env)
{
	E->DeleteGlobalRef(env, log->java_log);
}
