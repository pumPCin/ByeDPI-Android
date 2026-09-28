#include <errno.h>
#include <jni.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>

#include "byedpi/error.h"
#include "main.h"

extern int server_fd;
static pthread_mutex_t proxy_mutex = PTHREAD_MUTEX_INITIALIZER;
static int proxy_started;
static int proxy_running;

int __wrap_daemon(__attribute__((unused)) int nochdir, __attribute__((unused)) int noclose) {
    LOG(LOG_S, "daemon mode is not supported in the native worker");
    errno = EPERM;
    return -1;
}

JNIEXPORT jint JNICALL
Java_io_github_romanvht_byedpi_core_ByeDpiProxy_jniStartProxy(JNIEnv *env, __attribute__((unused)) jobject thiz, jobjectArray args) {
    pthread_mutex_lock(&proxy_mutex);
    if (proxy_started) {
        pthread_mutex_unlock(&proxy_mutex);
        LOG(LOG_S, "proxy process already used");
        return -1;
    }
    proxy_started = 1;
    pthread_mutex_unlock(&proxy_mutex);

    int argc = (*env)->GetArrayLength(env, args);
    char **argv = calloc(argc + 1, sizeof(char *));
    int result = -1;
    if (!argv) {
        return result;
    }

    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        if (!arg) {
            goto cleanup;
        }

        const char *arg_str = (*env)->GetStringUTFChars(env, arg, NULL);
        if (arg_str) {
            argv[i] = strdup(arg_str);
            (*env)->ReleaseStringUTFChars(env, arg, arg_str);
        }
        (*env)->DeleteLocalRef(env, arg);
        if (!argv[i]) {
            goto cleanup;
        }
    }

    LOG(LOG_S, "starting proxy with %d args", argc);
    pthread_mutex_lock(&proxy_mutex);
    server_fd = -1;
    proxy_running = 1;
    pthread_mutex_unlock(&proxy_mutex);

    result = main(argc, argv);

    pthread_mutex_lock(&proxy_mutex);
    proxy_running = 0;
    pthread_mutex_unlock(&proxy_mutex);
    LOG(LOG_S, "proxy return code %d", result);

cleanup:
    for (int i = 0; i < argc; i++) free(argv[i]);
    free(argv);
    return result;
}

JNIEXPORT jint JNICALL
Java_io_github_romanvht_byedpi_core_ByeDpiProxy_jniStopProxy(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject thiz) {
    LOG(LOG_S, "send shutdown to proxy");
    pthread_mutex_lock(&proxy_mutex);
    int result = proxy_running && server_fd >= 0 ? shutdown(server_fd, SHUT_RDWR) : -1;
    pthread_mutex_unlock(&proxy_mutex);
    return result;
}
