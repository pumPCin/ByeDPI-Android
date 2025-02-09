#include <string.h>
#include <netdb.h>

#include <jni.h>
#include <android/log.h>

#include "byedpi/error.h"
#include "byedpi/proxy.h"
#include "utils.h"

JNIEXPORT jint JNI_OnLoad(
        __attribute__((unused)) JavaVM *vm,
        __attribute__((unused)) void *reserved) {
    default_params = params;
    return JNI_VERSION_1_6;
}

JNIEXPORT jint JNICALL
Java_io_github_dovecoteescapee_byedpi_core_ByeDpiProxy_jniCreateSocket(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jobjectArray args) {
    int argc = (*env)->GetArrayLength(env, args);
    char *argv[argc];
    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        const char *arg_str = (*env)->GetStringUTFChars(env, arg, 0);
        argv[i] = strdup(arg_str);
        (*env)->ReleaseStringUTFChars(env, arg, arg_str);
    }

    int res = parse_args(argc, argv);
    if (res < 0) {
        uniperror("parse_args");
        return -1;
    }

    int fd = listen_socket((union sockaddr_u *)&params.laddr);
    if (fd < 0) {
        uniperror("listen_socket");
        return -1;
    }

    LOG(LOG_S, "listen_socket, fd: %d", fd);
    return fd;
}

JNIEXPORT jint JNICALL
Java_io_github_dovecoteescapee_byedpi_core_ByeDpiProxy_jniStartProxy(
        __attribute__((unused)) JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jint fd) {
    LOG(LOG_S, "start_proxy, fd: %d", fd);

    if (start_event_loop(fd) < 0) {
        uniperror("event_loop");
        return get_e();
    }

    return 0;
}

JNIEXPORT jint JNICALL
Java_io_github_dovecoteescapee_byedpi_core_ByeDpiProxy_jniStopProxy(
        __attribute__((unused)) JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jint fd) {
    LOG(LOG_S, "stop_proxy, fd: %d", fd);

    int res = shutdown(fd, SHUT_RDWR);
    reset_params();

    if (res < 0) {
        uniperror("shutdown");
        return get_e();
    }

    return 0;
}
