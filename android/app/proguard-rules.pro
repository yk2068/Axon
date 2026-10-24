# OkHttp probes optional TLS providers and platform classes at runtime; they are not on
# the Android classpath, so silence the missing references instead of failing the build.
-dontwarn okhttp3.internal.platform.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# ZXing selects a reader per format through MultiFormatReader; keep the QR path intact.
-keep class com.google.zxing.qrcode.** { *; }
-keep class com.google.zxing.common.** { *; }
