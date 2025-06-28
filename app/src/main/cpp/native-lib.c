#include <string.h>

#include <jni.h>
#include <getopt.h>
#include <signal.h>
#include <setjmp.h>

#include "byedpi/error.h"
#include "main.h"

extern int server_fd;
static int g_proxy_running = 0;
static jmp_buf crash_jmp_buf;

void sigsegv_handler(int sig) {
    LOG(LOG_S, "SIGSEGV caught in native code, signal: %d", sig);
    longjmp(crash_jmp_buf, 1);
    g_proxy_running = 0;
    clear_params();
}

JNIEXPORT jint JNICALL
Java_io_github_dovecoteescapee_byedpi_core_ByeDpiProxy_jniStartProxy(JNIEnv *env, __attribute__((unused)) jobject thiz, jobjectArray args) {
    if (g_proxy_running) {
        LOG(LOG_S, "proxy already running");
        return -1;
    }

    // Попытка избавится от краша приложения при остановке прокси
    struct sigaction sa;
    sa.sa_handler = sigsegv_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;
    sigaction(SIGSEGV, &sa, NULL);
    sigaction(SIGABRT, &sa, NULL);
    sigaction(SIGBUS, &sa, NULL);

    if (setjmp(crash_jmp_buf) != 0) {
        LOG(LOG_S, "crash proxy, continuing...");
        g_proxy_running = 0;
        return 0;
    }

    int argc = (*env)->GetArrayLength(env, args);
    char *argv[argc];
    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        const char *arg_str = (*env)->GetStringUTFChars(env, arg, 0);
        argv[i] = strdup(arg_str);
        (*env)->ReleaseStringUTFChars(env, arg, arg_str);
    }

    g_proxy_running = 1;
    optind = optreset = 1;
    LOG(LOG_S, "starting proxy with %d args", argc);

    int result = main(argc, argv);
    if (result < 0) {
        LOG(LOG_S, "proxy failed to start");
        g_proxy_running = 0;
        return result;
    }

    g_proxy_running = 0;
    return 0;
}

JNIEXPORT jint JNICALL
Java_io_github_dovecoteescapee_byedpi_core_ByeDpiProxy_jniStopProxy(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject thiz) {
    LOG(LOG_S, "send shutdown to proxy");

    if (!g_proxy_running) {
        LOG(LOG_S, "proxy is not running");
        return 0;
    }

    shutdown(server_fd, SHUT_RDWR);
    clear_params();

    g_proxy_running = 0;
    return 1;
}
