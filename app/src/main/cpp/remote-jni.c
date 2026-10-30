// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
// JNI bridge for PS5 Remote Play, adapted from chiaki-ng (android/app/src/main/cpp/chiaki-jni.c).
// Differences: no audio (the TV plays the sound, audio packets are dropped), no controller input
// (the player keeps using the controller connected to the console), threads are only detached
// when this code attached them.

#include <jni.h>

#include <chiaki/common.h>
#include <chiaki/log.h>
#include <chiaki/session.h>
#include <chiaki/discoveryservice.h>
#include <chiaki/regist.h>

#include <stdlib.h>
#include <string.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include "video-decoder.h"
#include "log.h"
#include "jni-common.h"

static char *strdup_jni(const char *str)
{
	if(!str)
		return NULL;
	char *r = strdup(str);
	if(!r)
		return NULL;
	// NewStringUTF needs modified UTF-8; console names are plain ASCII anyway.
	for(char *c = r; *c; c++)
	{
		if(*c & (1 << 7))
			*c = '?';
	}
	return r;
}

jobject jnistr_from_ascii(JNIEnv *env, const char *str)
{
	if(!str)
		return NULL;
	char *s = strdup_jni(str);
	if(!s)
		return NULL;
	jobject r = E->NewStringUTF(env, s);
	free(s);
	return r;
}

static jbyteArray jnibytearray_create(JNIEnv *env, const uint8_t *buf, size_t buf_size)
{
	jbyteArray r = E->NewByteArray(env, (jsize)buf_size);
	E->SetByteArrayRegion(env, r, 0, (jsize)buf_size, (const jbyte *)buf);
	return r;
}

static jobject get_kotlin_object_instance(JNIEnv *env, const char *id)
{
	size_t idlen = strlen(id);
	char *sig = malloc(idlen + 3);
	if(!sig)
		return NULL;
	sig[0] = 'L';
	memcpy(sig + 1, id, idlen);
	sig[1 + idlen] = ';';
	sig[2 + idlen] = '\0';
	jclass cls = E->FindClass(env, id);
	jfieldID field_id = E->GetStaticFieldID(env, cls, "INSTANCE", sig);
	jobject r = E->GetStaticObjectField(env, cls, field_id);
	free(sig);
	return r;
}

static ChiakiLog global_log;
JavaVM *global_vm;

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
	global_vm = vm;
	android_chiaki_logcat_log_init(&global_log, CHIAKI_LOG_ALL & ~CHIAKI_LOG_VERBOSE);
	ChiakiErrorCode err = chiaki_lib_init();
	CHIAKI_LOGI(&global_log, "Remote Play library init: %s", chiaki_error_string(err));
	return JNI_VERSION;
}

JNIEnv *attach_thread_jni(bool *attached)
{
	JNIEnv *env;
	*attached = false;
	if((*global_vm)->GetEnv(global_vm, (void **)&env, JNI_VERSION) == JNI_OK)
		return env;
	if((*global_vm)->AttachCurrentThread(global_vm, &env, NULL) == 0)
	{
		*attached = true;
		return env;
	}
	CHIAKI_LOGE(&global_log, "Failed to get JNIEnv from JavaVM or attach");
	return NULL;
}

void detach_thread_jni(bool attached)
{
	if(attached)
		(*global_vm)->DetachCurrentThread(global_vm);
}

JNIEXPORT jstring JNICALL JNI_FCN(errorCodeToString)(JNIEnv *env, jclass cls, jint value)
{
	return E->NewStringUTF(env, chiaki_error_string((ChiakiErrorCode)value));
}

JNIEXPORT jstring JNICALL JNI_FCN(quitReasonToString)(JNIEnv *env, jclass cls, jint value)
{
	return E->NewStringUTF(env, chiaki_quit_reason_string((ChiakiQuitReason)value));
}

JNIEXPORT jboolean JNICALL JNI_FCN(quitReasonIsError)(JNIEnv *env, jclass cls, jint value)
{
	return chiaki_quit_reason_is_error((ChiakiQuitReason)value);
}

/* ---------- Session ---------- */

typedef struct android_chiaki_session_t
{
	ChiakiSession session;
	ChiakiLog log;
	jobject java_session;
	jmethodID java_session_event_connected_meth;
	jmethodID java_session_event_login_pin_request_meth;
	jmethodID java_session_event_quit_meth;
	AndroidChiakiVideoDecoder video_decoder;
} AndroidChiakiSession;

static void android_chiaki_event_cb(ChiakiEvent *event, void *user)
{
	AndroidChiakiSession *session = user;
	bool attached = false;
	JNIEnv *env = attach_thread_jni(&attached);
	if(!env)
		return;

	switch(event->type)
	{
		case CHIAKI_EVENT_CONNECTED:
			E->CallVoidMethod(env, session->java_session, session->java_session_event_connected_meth);
			break;
		case CHIAKI_EVENT_LOGIN_PIN_REQUEST:
			E->CallVoidMethod(env, session->java_session, session->java_session_event_login_pin_request_meth,
					(jboolean)event->login_pin_request.pin_incorrect);
			break;
		case CHIAKI_EVENT_QUIT:
		{
			jobject reason_str = jnistr_from_ascii(env, event->quit.reason_str);
			E->CallVoidMethod(env, session->java_session, session->java_session_event_quit_meth,
					(jint)event->quit.reason, reason_str);
			if(reason_str)
				E->DeleteLocalRef(env, reason_str);
			break;
		}
		default:
			break;
	}

	detach_thread_jni(attached);
}

static bool copy_fixed_bytes(JNIEnv *env, jbyteArray array, void *dst, size_t size)
{
	if(!array || E->GetArrayLength(env, array) != (jsize)size)
		return false;
	E->GetByteArrayRegion(env, array, 0, (jsize)size, (jbyte *)dst);
	return true;
}

JNIEXPORT void JNICALL JNI_FCN(sessionCreate)(JNIEnv *env, jclass cls, jobject result, jobject connect_info_obj, jobject java_session)
{
	ChiakiErrorCode err = CHIAKI_ERR_SUCCESS;
	AndroidChiakiSession *session = NULL;
	char *host_str = NULL;
	jclass result_class = E->GetObjectClass(env, result);

	jclass connect_info_class = E->GetObjectClass(env, connect_info_obj);
	jboolean ps5 = E->GetBooleanField(env, connect_info_obj, E->GetFieldID(env, connect_info_class, "ps5", "Z"));
	jstring host_string = E->GetObjectField(env, connect_info_obj, E->GetFieldID(env, connect_info_class, "host", "Ljava/lang/String;"));
	jbyteArray regist_key_array = E->GetObjectField(env, connect_info_obj, E->GetFieldID(env, connect_info_class, "registKey", "[B"));
	jbyteArray morning_array = E->GetObjectField(env, connect_info_obj, E->GetFieldID(env, connect_info_class, "morning", "[B"));
	jobject profile_obj = E->GetObjectField(env, connect_info_obj, E->GetFieldID(env, connect_info_class, "videoProfile", "L"BASE_PACKAGE"/ConnectVideoProfile;"));
	jclass profile_class = E->GetObjectClass(env, profile_obj);

	ChiakiConnectInfo connect_info;
	memset(&connect_info, 0, sizeof(connect_info));
	connect_info.ps5 = ps5;

	const char *str_borrow = E->GetStringUTFChars(env, host_string, NULL);
	connect_info.host = host_str = strdup(str_borrow);
	E->ReleaseStringUTFChars(env, host_string, str_borrow);
	if(!host_str)
	{
		err = CHIAKI_ERR_MEMORY;
		goto beach;
	}

	if(!copy_fixed_bytes(env, regist_key_array, connect_info.regist_key, sizeof(connect_info.regist_key))
		|| !copy_fixed_bytes(env, morning_array, connect_info.morning, sizeof(connect_info.morning)))
	{
		CHIAKI_LOGE(&global_log, "Registration keys passed from Kotlin have an invalid length");
		err = CHIAKI_ERR_INVALID_DATA;
		goto beach;
	}

	connect_info.video_profile.width = (unsigned int)E->GetIntField(env, profile_obj, E->GetFieldID(env, profile_class, "width", "I"));
	connect_info.video_profile.height = (unsigned int)E->GetIntField(env, profile_obj, E->GetFieldID(env, profile_class, "height", "I"));
	connect_info.video_profile.max_fps = (unsigned int)E->GetIntField(env, profile_obj, E->GetFieldID(env, profile_class, "maxFps", "I"));
	connect_info.video_profile.bitrate = (unsigned int)E->GetIntField(env, profile_obj, E->GetFieldID(env, profile_class, "bitrate", "I"));
	connect_info.video_profile.codec = (ChiakiCodec)E->GetIntField(env, profile_obj, E->GetFieldID(env, profile_class, "codec", "I"));
	connect_info.video_profile_auto_downgrade = true;
	connect_info.audio_video_disabled = CHIAKI_AUDIO_DISABLED;
	connect_info.packet_loss_max = 0.05;
	connect_info.enable_idr_on_fec_failure = true;

	session = calloc(1, sizeof(AndroidChiakiSession));
	if(!session)
	{
		err = CHIAKI_ERR_MEMORY;
		goto beach;
	}
	android_chiaki_logcat_log_init(&session->log, CHIAKI_LOG_ALL & ~CHIAKI_LOG_VERBOSE);

	err = android_chiaki_video_decoder_init(&session->video_decoder, &session->log,
			(int32_t)connect_info.video_profile.width, (int32_t)connect_info.video_profile.height,
			connect_info.ps5 ? connect_info.video_profile.codec : CHIAKI_CODEC_H264);
	if(err != CHIAKI_ERR_SUCCESS)
	{
		free(session);
		session = NULL;
		goto beach;
	}

	err = chiaki_session_init(&session->session, &connect_info, &session->log);
	if(err != CHIAKI_ERR_SUCCESS)
	{
		CHIAKI_LOGE(&session->log, "Remote Play session failed to init");
		android_chiaki_video_decoder_fini(&session->video_decoder);
		free(session);
		session = NULL;
		goto beach;
	}

	session->java_session = E->NewGlobalRef(env, java_session);
	jclass session_class = E->GetObjectClass(env, session->java_session);
	session->java_session_event_connected_meth = E->GetMethodID(env, session_class, "eventConnected", "()V");
	session->java_session_event_login_pin_request_meth = E->GetMethodID(env, session_class, "eventLoginPinRequest", "(Z)V");
	session->java_session_event_quit_meth = E->GetMethodID(env, session_class, "eventQuit", "(ILjava/lang/String;)V");

	chiaki_session_set_event_cb(&session->session, android_chiaki_event_cb, session);
	chiaki_session_set_video_sample_cb(&session->session, android_chiaki_video_decoder_video_sample, &session->video_decoder);

beach:
	free(host_str);
	E->SetIntField(env, result, E->GetFieldID(env, result_class, "errorCode", "I"), (jint)err);
	E->SetLongField(env, result, E->GetFieldID(env, result_class, "ptr", "J"), (jlong)(intptr_t)session);
}

JNIEXPORT void JNICALL JNI_FCN(sessionFree)(JNIEnv *env, jclass cls, jlong ptr)
{
	AndroidChiakiSession *session = (AndroidChiakiSession *)(intptr_t)ptr;
	if(!session)
		return;
	chiaki_session_fini(&session->session);
	android_chiaki_video_decoder_fini(&session->video_decoder);
	E->DeleteGlobalRef(env, session->java_session);
	free(session);
}

JNIEXPORT jint JNICALL JNI_FCN(sessionStart)(JNIEnv *env, jclass cls, jlong ptr)
{
	AndroidChiakiSession *session = (AndroidChiakiSession *)(intptr_t)ptr;
	return chiaki_session_start(&session->session);
}

JNIEXPORT jint JNICALL JNI_FCN(sessionStop)(JNIEnv *env, jclass cls, jlong ptr)
{
	AndroidChiakiSession *session = (AndroidChiakiSession *)(intptr_t)ptr;
	return chiaki_session_stop(&session->session);
}

JNIEXPORT jint JNICALL JNI_FCN(sessionJoin)(JNIEnv *env, jclass cls, jlong ptr)
{
	AndroidChiakiSession *session = (AndroidChiakiSession *)(intptr_t)ptr;
	return chiaki_session_join(&session->session);
}

JNIEXPORT void JNICALL JNI_FCN(sessionSetSurface)(JNIEnv *env, jclass cls, jlong ptr, jobject surface)
{
	AndroidChiakiSession *session = (AndroidChiakiSession *)(intptr_t)ptr;
	android_chiaki_video_decoder_set_surface(&session->video_decoder, env, surface);
}

JNIEXPORT void JNICALL JNI_FCN(sessionSetLoginPin)(JNIEnv *env, jclass cls, jlong ptr, jstring pin_java)
{
	AndroidChiakiSession *session = (AndroidChiakiSession *)(intptr_t)ptr;
	const char *pin = E->GetStringUTFChars(env, pin_java, NULL);
	chiaki_session_set_login_pin(&session->session, (const uint8_t *)pin, strlen(pin));
	E->ReleaseStringUTFChars(env, pin_java, pin);
}

/* ---------- Discovery ---------- */

typedef struct android_discovery_service_t
{
	ChiakiDiscoveryService service;
	jobject java_service;
	jmethodID java_service_hosts_updated_meth;
	jclass host_class;
	jmethodID host_ctor;
	jobject host_state_unknown;
	jobject host_state_ready;
	jobject host_state_standby;
} AndroidDiscoveryService;

static void android_discovery_service_cb(ChiakiDiscoveryHost *hosts, size_t hosts_count, void *user)
{
	AndroidDiscoveryService *service = user;
	bool attached = false;
	JNIEnv *env = attach_thread_jni(&attached);
	if(!env)
		return;

	jobjectArray r = E->NewObjectArray(env, (jsize)hosts_count, service->host_class, NULL);
	for(size_t i = 0; i < hosts_count; i++)
	{
		ChiakiDiscoveryHost *host = hosts + i;
		jobject state;
		switch(host->state)
		{
			case CHIAKI_DISCOVERY_HOST_STATE_STANDBY: state = service->host_state_standby; break;
			case CHIAKI_DISCOVERY_HOST_STATE_READY: state = service->host_state_ready; break;
			default: state = service->host_state_unknown; break;
		}
		jobject o = E->NewObject(env, service->host_class, service->host_ctor,
				state,
				(jint)host->host_request_port,
				jnistr_from_ascii(env, host->host_addr),
				jnistr_from_ascii(env, host->system_version),
				jnistr_from_ascii(env, host->device_discovery_protocol_version),
				jnistr_from_ascii(env, host->host_name),
				jnistr_from_ascii(env, host->host_type),
				jnistr_from_ascii(env, host->host_id),
				jnistr_from_ascii(env, host->running_app_titleid),
				jnistr_from_ascii(env, host->running_app_name));
		E->SetObjectArrayElement(env, r, (jsize)i, o);
		E->DeleteLocalRef(env, o);
	}

	E->CallVoidMethod(env, service->java_service, service->java_service_hosts_updated_meth, r);
	E->DeleteLocalRef(env, r);
	detach_thread_jni(attached);
}

static void discovery_service_release_refs(JNIEnv *env, AndroidDiscoveryService *service)
{
	E->DeleteGlobalRef(env, service->java_service);
	E->DeleteGlobalRef(env, service->host_state_unknown);
	E->DeleteGlobalRef(env, service->host_state_standby);
	E->DeleteGlobalRef(env, service->host_state_ready);
	E->DeleteGlobalRef(env, service->host_class);
}

JNIEXPORT void JNICALL JNI_FCN(discoveryServiceCreate)(JNIEnv *env, jclass cls, jobject result, jstring broadcast_addr_java, jint port, jobject java_service)
{
	jclass result_class = E->GetObjectClass(env, result);
	ChiakiErrorCode err = CHIAKI_ERR_SUCCESS;

	AndroidDiscoveryService *service = calloc(1, sizeof(AndroidDiscoveryService));
	struct sockaddr_in *send_addr = calloc(1, sizeof(struct sockaddr_in));
	if(!service || !send_addr)
	{
		err = CHIAKI_ERR_MEMORY;
		free(service);
		service = NULL;
		goto beach;
	}

	const char *broadcast_addr = E->GetStringUTFChars(env, broadcast_addr_java, NULL);
	send_addr->sin_family = AF_INET;
	send_addr->sin_port = htons((uint16_t)port);
	int parsed = inet_pton(AF_INET, broadcast_addr, &send_addr->sin_addr);
	E->ReleaseStringUTFChars(env, broadcast_addr_java, broadcast_addr);
	if(parsed != 1)
	{
		err = CHIAKI_ERR_INVALID_DATA;
		free(service);
		service = NULL;
		goto beach;
	}

	ChiakiDiscoveryServiceOptions options;
	memset(&options, 0, sizeof(options));
	options.hosts_max = 16;
	options.host_drop_pings = 3;
	options.ping_ms = 500;
	options.send_addr = (struct sockaddr_storage *)send_addr;
	options.send_addr_size = sizeof(struct sockaddr_in);
	options.cb = android_discovery_service_cb;
	options.cb_user = service;

	service->java_service = E->NewGlobalRef(env, java_service);
	service->java_service_hosts_updated_meth = E->GetMethodID(env, E->GetObjectClass(env, java_service), "hostsUpdated", "([L"BASE_PACKAGE"/DiscoveryHost;)V");
	service->host_class = E->NewGlobalRef(env, E->FindClass(env, BASE_PACKAGE"/DiscoveryHost"));
	service->host_ctor = E->GetMethodID(env, service->host_class, "<init>", "("
			"L"BASE_PACKAGE"/DiscoveryHost$State;"
			"I"                  // hostRequestPort
			"Ljava/lang/String;" // hostAddr
			"Ljava/lang/String;" // systemVersion
			"Ljava/lang/String;" // deviceDiscoveryProtocolVersion
			"Ljava/lang/String;" // hostName
			"Ljava/lang/String;" // hostType
			"Ljava/lang/String;" // hostId
			"Ljava/lang/String;" // runningAppTitleId
			"Ljava/lang/String;" // runningAppName
			")V");
	jclass state_class = E->FindClass(env, BASE_PACKAGE"/DiscoveryHost$State");
	const char *state_sig = "L"BASE_PACKAGE"/DiscoveryHost$State;";
	service->host_state_unknown = E->NewGlobalRef(env, E->GetStaticObjectField(env, state_class, E->GetStaticFieldID(env, state_class, "UNKNOWN", state_sig)));
	service->host_state_standby = E->NewGlobalRef(env, E->GetStaticObjectField(env, state_class, E->GetStaticFieldID(env, state_class, "STANDBY", state_sig)));
	service->host_state_ready = E->NewGlobalRef(env, E->GetStaticObjectField(env, state_class, E->GetStaticFieldID(env, state_class, "READY", state_sig)));

	// chiaki_discovery_service_init copies the address, so it is freed below in every case.
	err = chiaki_discovery_service_init(&service->service, &options, &global_log);
	if(err != CHIAKI_ERR_SUCCESS)
	{
		CHIAKI_LOGE(&global_log, "Failed to create discovery service");
		discovery_service_release_refs(env, service);
		free(service);
		service = NULL;
	}

beach:
	free(send_addr);
	E->SetIntField(env, result, E->GetFieldID(env, result_class, "errorCode", "I"), (jint)err);
	E->SetLongField(env, result, E->GetFieldID(env, result_class, "ptr", "J"), (jlong)(intptr_t)service);
}

JNIEXPORT void JNICALL JNI_FCN(discoveryServiceFree)(JNIEnv *env, jclass cls, jlong ptr)
{
	AndroidDiscoveryService *service = (AndroidDiscoveryService *)(intptr_t)ptr;
	if(!service)
		return;
	chiaki_discovery_service_fini(&service->service);
	discovery_service_release_refs(env, service);
	free(service);
}

JNIEXPORT jint JNICALL JNI_FCN(discoveryWakeup)(JNIEnv *env, jclass cls, jlong service_ptr, jstring host_string, jlong user_credential, jboolean ps5)
{
	AndroidDiscoveryService *service = (AndroidDiscoveryService *)(intptr_t)service_ptr;
	const char *host = E->GetStringUTFChars(env, host_string, NULL);
	ChiakiErrorCode r = chiaki_discovery_wakeup(&global_log, service ? &service->service.discovery : NULL,
			host, (uint64_t)user_credential, ps5);
	E->ReleaseStringUTFChars(env, host_string, host);
	return r;
}

/* ---------- Registration ---------- */

typedef struct android_chiaki_regist_t
{
	AndroidChiakiJNILog log;
	ChiakiRegist regist;
	jobject java_regist;
	jmethodID java_regist_event_meth;
	jclass java_target_class;
	jobject java_regist_event_canceled;
	jobject java_regist_event_failed;
	jclass java_regist_event_success_class;
	jmethodID java_regist_event_success_ctor;
	jclass java_regist_host_class;
	jmethodID java_regist_host_ctor;
} AndroidChiakiRegist;

static jobject create_jni_target(JNIEnv *env, jclass target_class, ChiakiTarget target)
{
	jmethodID meth = E->GetStaticMethodID(env, target_class, "fromValue", "(I)L"BASE_PACKAGE"/Target;");
	return E->CallStaticObjectMethod(env, target_class, meth, (jint)target);
}

static void android_chiaki_regist_cb(ChiakiRegistEvent *event, void *user)
{
	AndroidChiakiRegist *regist = user;
	bool attached = false;
	JNIEnv *env = attach_thread_jni(&attached);
	if(!env)
		return;

	jobject java_event = NULL;
	switch(event->type)
	{
		case CHIAKI_REGIST_EVENT_TYPE_FINISHED_CANCELED:
			java_event = regist->java_regist_event_canceled;
			break;
		case CHIAKI_REGIST_EVENT_TYPE_FINISHED_FAILED:
			java_event = regist->java_regist_event_failed;
			break;
		case CHIAKI_REGIST_EVENT_TYPE_FINISHED_SUCCESS:
		{
			ChiakiRegisteredHost *host = event->registered_host;
			jobject java_host = E->NewObject(env, regist->java_regist_host_class, regist->java_regist_host_ctor,
					create_jni_target(env, regist->java_target_class, host->target),
					jnistr_from_ascii(env, host->ap_ssid),
					jnistr_from_ascii(env, host->ap_bssid),
					jnistr_from_ascii(env, host->ap_key),
					jnistr_from_ascii(env, host->ap_name),
					jnibytearray_create(env, host->server_mac, sizeof(host->server_mac)),
					jnistr_from_ascii(env, host->server_nickname),
					jnibytearray_create(env, (const uint8_t *)host->rp_regist_key, sizeof(host->rp_regist_key)),
					(jint)host->rp_key_type,
					jnibytearray_create(env, host->rp_key, sizeof(host->rp_key)));
			java_event = E->NewObject(env, regist->java_regist_event_success_class, regist->java_regist_event_success_ctor, java_host);
			break;
		}
		default:
			break;
	}

	if(java_event)
		E->CallVoidMethod(env, regist->java_regist, regist->java_regist_event_meth, java_event);

	detach_thread_jni(attached);
}

static void android_chiaki_regist_fini_partial(JNIEnv *env, AndroidChiakiRegist *regist)
{
	android_chiaki_jni_log_fini(&regist->log, env);
	E->DeleteGlobalRef(env, regist->java_regist);
	E->DeleteGlobalRef(env, regist->java_target_class);
	E->DeleteGlobalRef(env, regist->java_regist_event_canceled);
	E->DeleteGlobalRef(env, regist->java_regist_event_failed);
	E->DeleteGlobalRef(env, regist->java_regist_event_success_class);
	E->DeleteGlobalRef(env, regist->java_regist_host_class);
}

JNIEXPORT void JNICALL JNI_FCN(registStart)(JNIEnv *env, jclass cls, jobject result, jobject regist_info_obj, jobject log_obj, jobject java_regist)
{
	jclass result_class = E->GetObjectClass(env, result);
	ChiakiErrorCode err = CHIAKI_ERR_SUCCESS;
	AndroidChiakiRegist *regist = calloc(1, sizeof(AndroidChiakiRegist));
	if(!regist)
	{
		err = CHIAKI_ERR_MEMORY;
		goto beach;
	}

	android_chiaki_jni_log_init(&regist->log, env, log_obj);

	regist->java_regist = E->NewGlobalRef(env, java_regist);
	regist->java_regist_event_meth = E->GetMethodID(env, E->GetObjectClass(env, regist->java_regist), "event", "(L"BASE_PACKAGE"/RegistEvent;)V");
	regist->java_target_class = E->NewGlobalRef(env, E->FindClass(env, BASE_PACKAGE"/Target"));
	regist->java_regist_event_canceled = E->NewGlobalRef(env, get_kotlin_object_instance(env, BASE_PACKAGE"/RegistEventCanceled"));
	regist->java_regist_event_failed = E->NewGlobalRef(env, get_kotlin_object_instance(env, BASE_PACKAGE"/RegistEventFailed"));
	regist->java_regist_event_success_class = E->NewGlobalRef(env, E->FindClass(env, BASE_PACKAGE"/RegistEventSuccess"));
	regist->java_regist_event_success_ctor = E->GetMethodID(env, regist->java_regist_event_success_class, "<init>", "(L"BASE_PACKAGE"/RegistHost;)V");
	regist->java_regist_host_class = E->NewGlobalRef(env, E->FindClass(env, BASE_PACKAGE"/RegistHost"));
	regist->java_regist_host_ctor = E->GetMethodID(env, regist->java_regist_host_class, "<init>", "("
			"L"BASE_PACKAGE"/Target;" // target
			"Ljava/lang/String;"      // apSsid
			"Ljava/lang/String;"      // apBssid
			"Ljava/lang/String;"      // apKey
			"Ljava/lang/String;"      // apName
			"[B"                      // serverMac
			"Ljava/lang/String;"      // serverNickname
			"[B"                      // rpRegistKey
			"I"                       // rpKeyType
			"[B"                      // rpKey
			")V");

	jclass regist_info_class = E->GetObjectClass(env, regist_info_obj);
	jstring host_string = E->GetObjectField(env, regist_info_obj, E->GetFieldID(env, regist_info_class, "host", "Ljava/lang/String;"));
	jbyteArray account_id_array = E->GetObjectField(env, regist_info_obj, E->GetFieldID(env, regist_info_class, "psnAccountId", "[B"));

	ChiakiRegistInfo regist_info;
	memset(&regist_info, 0, sizeof(regist_info));
	regist_info.target = (ChiakiTarget)E->GetIntField(env, regist_info_obj, E->GetFieldID(env, regist_info_class, "target", "I"));
	regist_info.host = E->GetStringUTFChars(env, host_string, NULL);
	regist_info.broadcast = E->GetBooleanField(env, regist_info_obj, E->GetFieldID(env, regist_info_class, "broadcast", "Z"));
	regist_info.psn_online_id = NULL;
	copy_fixed_bytes(env, account_id_array, regist_info.psn_account_id, sizeof(regist_info.psn_account_id));
	regist_info.pin = (uint32_t)E->GetIntField(env, regist_info_obj, E->GetFieldID(env, regist_info_class, "pin", "I"));

	err = chiaki_regist_start(&regist->regist, &regist->log.log, &regist_info, android_chiaki_regist_cb, regist);
	E->ReleaseStringUTFChars(env, host_string, regist_info.host);

	if(err != CHIAKI_ERR_SUCCESS)
	{
		android_chiaki_regist_fini_partial(env, regist);
		free(regist);
		regist = NULL;
	}

beach:
	E->SetIntField(env, result, E->GetFieldID(env, result_class, "errorCode", "I"), (jint)err);
	E->SetLongField(env, result, E->GetFieldID(env, result_class, "ptr", "J"), (jlong)(intptr_t)regist);
}

JNIEXPORT void JNICALL JNI_FCN(registStop)(JNIEnv *env, jclass cls, jlong ptr)
{
	AndroidChiakiRegist *regist = (AndroidChiakiRegist *)(intptr_t)ptr;
	if(regist)
		chiaki_regist_stop(&regist->regist);
}

JNIEXPORT void JNICALL JNI_FCN(registFree)(JNIEnv *env, jclass cls, jlong ptr)
{
	AndroidChiakiRegist *regist = (AndroidChiakiRegist *)(intptr_t)ptr;
	if(!regist)
		return;
	chiaki_regist_fini(&regist->regist);
	android_chiaki_regist_fini_partial(env, regist);
	free(regist);
}
