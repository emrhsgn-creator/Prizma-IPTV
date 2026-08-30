// FFmpeg ses çözücüsü yalnızca derlenmiş kitaplıklar hazırsa APK'ya giriyor.
// Hazır değilse yerel derleme kırılmasın diye yerel derleme atlanıyor; uygulama
// o zaman eskisi gibi yalnızca cihazın çözücülerini kullanır.
val ffmpegLibs = file("src/main/jni/ffmpeg/android-libs")
val ffmpegReady = ffmpegLibs.isDirectory && ffmpegLibs.list()?.isNotEmpty() == true

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.prizma.iptv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.prizma.iptv"
        minSdk = 21
        targetSdk = 35
        versionCode = 16
        versionName = "0.3.2"

        if (ffmpegReady) {
            ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
        }
    }

    if (ffmpegReady) {
        ndkVersion = "26.1.10909125"
        externalNativeBuild {
            cmake {
                path = file("src/main/jni/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
signingConfigs {
        create("release") {
            val ksFile = rootProject.file("prizma.jks")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = "prizma2026"
                keyAlias = "prizma"
                keyPassword = "prizma2026"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (rootProject.file("prizma.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            if (rootProject.file("prizma.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Projeye alinan FFmpeg cozucu kaynaklari media3'un kendi derlemesinde
    // uretiliyor ve orada gecerli olan opt-in ayarlari burada yok. Bir lint
    // bulgusunun APK uretimini durdurmasini istemiyoruz.
    lint {
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    // FFmpeg cozucu kaynaklari SimpleDecoder ve DecoderInputBuffer gibi
    // siniflari dogrudan kullaniyor; media3-exoplayer bunlari derleme
    // yoluna acmiyor, o yuzden acikca ekleniyor.
    implementation("androidx.media3:media3-decoder:1.4.1")
    // Ayni kaynaklar @MonotonicNonNull kullaniyor. Anotasyon calisma
    // zamaninda gerekmedigi icin yalnizca derlemeye ekleniyor.
    compileOnly("org.checkerframework:checker-qual:3.42.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
}
