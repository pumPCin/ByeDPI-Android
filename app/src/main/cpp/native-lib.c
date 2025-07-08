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

struct params default_params = {
        .await_int = 10,
        .cache_ttl = 100800,
        .ipv6 = 1,
        .resolve = 1,
        .udp = 1,
        .max_open = 512,
        .bfsize = 16384,
        .baddr = {
            .in6 = { .sin6_family = AF_INET6 }
        },
        .laddr = {
            .in = { .sin_family = AF_INET }
        },
        .debug = 0
};

void reset_params(void) {
    clear_params();
    params = default_params;
}

void sigsegv_handler(int sig) {
    if (sig == 11) {
        longjmp(crash_jmp_buf, 1);
    } else {
        shutdown(server_fd, SHUT_RDWR);
    }

    g_proxy_running = 0;
    reset_params();
}

JNIEXPORT jint JNICALL
Java_io_github_dovecoteescapee_byedpi_core_ByeDpiProxy_jniStartProxy(JNIEnv *env, __attribute__((unused)) jobject thiz, jobjectArray args) {
    if (g_proxy_running) {
        return -1;
    }

    struct sigaction sa;
    sa.sa_handler = sigsegv_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;

    sigaction(SIGSEGV, &sa, NULL);
    sigaction(SIGABRT, &sa, NULL);
    sigaction(SIGBUS, &sa, NULL);

    if (setjmp(crash_jmp_buf) != 0) {
        g_proxy_running = 0;
        reset_params();
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

    int result = main(argc, argv);

    g_proxy_running = 0;
    reset_params();

    return result;
}

JNIEXPORT jint JNICALL
Java_io_github_dovecoteescapee_byedpi_core_ByeDpiProxy_jniStopProxy(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject thiz) {

    if (!g_proxy_running) {
        return -1;
    }

    shutdown(server_fd, SHUT_RDWR);
    g_proxy_running = 0;
    reset_params();

    return 0;
}

JNIEXPORT jint JNICALL
Java_io_github_dovecoteescapee_byedpi_core_ByeDpiProxy_jniForceClose(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject thiz) {

    if (close(server_fd) == -1) {
        return -1;
    }

    return 0;
}


