plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.wire)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.biliclient"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.biliclient"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Chip-neutral: arm64-v8a for real devices, x86_64 for emulator. No armeabi-v7a/x86.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    signingConfigs {
        create("release") {
            // 临时用 debug keystore 签名，够真机安装验证；正式发布时替换为正式 keystore。
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // JVM unit tests use real network (P0 verification); allow them to run with default config.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Room schema 导出目录（doc 11 exportSchema=true）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Wire：从 src/main/proto/dm.proto 生成 Kotlin（弹幕模型，doc 06）
wire {
    kotlin {}
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Network + serialization (P0 risk-control core)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.zxing.core)
    implementation(libs.coil.compose)

    // Room（账号/登录cookie、下载、搜索历史 —— 见 doc 11）
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Wire（弹幕 protobuf —— 见 doc 06）
    implementation(libs.wire.runtime)

    // Media3 / ExoPlayer（DASH 播放 —— 见 doc 05）
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)

    // ffmpeg 合流（doc 08；ffmpeg-kit 官方已下架，用维护中 fork antonkarpenko full）
    implementation(libs.ffmpeg.kit.full)

    // 下载续传兜底（doc 08）
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Unit tests (JVM) — P0 verified here (AbConvert/Murmur3/Wbi + live network nav/wbi-view)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    testImplementation(libs.retrofit)
    testImplementation(libs.retrofit.serialization)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.okhttp)
    androidTestImplementation(libs.retrofit)
    androidTestImplementation(libs.retrofit.serialization)
    androidTestImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
