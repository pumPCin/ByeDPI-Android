import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

val abis = setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

android {
    namespace = "io.github.dovecoteescapee.byedpi"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "io.github.dovecoteescapee.byedpi"
        minSdk = 23
        //noinspection OldTargetApi
        targetSdk = 34
        versionCode = 1733
        versionName = "1.7.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(abis)
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        release {
            buildConfigField("String", "VERSION_NAME",  "\"${defaultConfig.versionName}\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
        }
        debug {
            buildConfigField("String", "VERSION_NAME",  "\"${defaultConfig.versionName}-debug\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // https://android.izzysoft.de/articles/named/iod-scan-apkchecks?lang=en#blobs
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*abis.toTypedArray())
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

afterEvaluate {
    tasks.register<Exec>("runNdkBuild") {
        group = "build"

        val androidComponents = extensions.findByType<com.android.build.api.variant.ApplicationAndroidComponentsExtension>()
        val sdkComponents = androidComponents?.sdkComponents
        val ndkDir = sdkComponents?.ndkDirectory?.get()?.asFile?.absolutePath 
            ?: System.getenv("ANDROID_NDK_HOME") 
            ?: System.getenv("ANDROID_NDK_ROOT")
            ?: throw GradleException("NDK Path not found.")
        executable = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "$ndkDir\\ndk-build.cmd"
        } else {
            "$ndkDir/ndk-build"
        }
        args(
            "NDK_PROJECT_PATH=build/intermediates/ndkBuild",
            "NDK_LIBS_OUT=src/main/jniLibs",
            "APP_BUILD_SCRIPT=src/main/jni/Android.mk",
            "NDK_APPLICATION_MK=src/main/jni/Application.mk"
        )
        doFirst {
            println("NDK Path: $ndkDir")
        }
    }
    tasks.named("preBuild") {
        dependsOn("runNdkBuild")
    }
}

tasks.preBuild {
    dependsOn("runNdkBuild")
}
