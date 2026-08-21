# --- Prizma IPTV R8 kuralları ---

# Kotlin / Coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Media3 / ExoPlayer — bazı bileşenler yansıma ile yükleniyor.
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.decoder.** { *; }

# FFmpeg uzantısı JNI ile bağlanıyor, isimleri korunmalı.
-keep class io.github.anilbeesetti.nextlib.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Coil
-dontwarn coil.**

# Uygulama modelleri (org.json ile elle serileştiriliyor, yine de güvenli taraf)
-keep class com.prizma.iptv.data.model.** { *; }

# Compose zaten kendi kurallarını taşır; ek uyarıları bastır.
-dontwarn androidx.compose.**

# Satır numaralarını kaynak eşlemesi için sakla
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
