import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// İmza bilgileri repoda tutulmaz. Sırasıyla ortam değişkeni, keystore.properties
// ve gradle -P parametrelerine bakılır. Hiçbiri yoksa release imzasız derlenir.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(env: String, prop: String): String? =
    System.getenv(env)
        ?: keystoreProps.getProperty(prop)
        ?: (project.findProperty(prop) as String?)

val ksFile = rootProject.file(secret("PRIZMA_KEYSTORE", "storeFile") ?: "prizma.jks")
val ksPass = secret("PRIZMA_KEYSTORE_PASSWORD", "storePassword")
val ksAlias = secret("PRIZMA_KEY_ALIAS", "keyAlias") ?: "prizma"
val ksAliasPass = secret("PRIZMA_KEY_PASSWORD", "keyPassword") ?: ksPass
val canSign = ksFile.exists() && !ksPass.isNullOrBlank()

android {
    namespace = "com.prizma.iptv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.prizma.iptv"
        minSdk = 21
        targetSdk = 35
        versionCode = 7
        versionName = "2.0.4"

        // Tek APK uretilir; dort mimariyi de icerir ve her cihaza kurulur.
        // ABI'ye gore bolmek elden kurulumda fayda saglamiyordu.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            if (canSign) {
                storeFile = ksFile
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = ksAliasPass
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (canSign) signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "kotlin/**",
                "DebugProbesKt.bin"
            )
        }
        jniLibs { useLegacyPackaging = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// nextlib, kendi Kotlin stdlib sürümünü çekiyor; derleyiciyle aynı sürüme sabitliyoruz.
configurations.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21")
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-common:$media3")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")

    // AC3 / E-AC3 / DTS / TrueHD ve HEVC için FFmpeg tabanlı yazılım kod çözücüler.
    // Lisans: GPL-3.0 (bkz. README > Lisans).
    implementation("com.github.anilbeesetti.nextlib:nextlib-media3ext:0.8.3")
}
