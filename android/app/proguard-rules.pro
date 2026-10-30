# ProGuard Rules for StockWebcam
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep <methods>;
    @androidx.annotation.Keep <fields>;
}

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

