# ProGuard rules for AeroCine Camera
-keepattributes *Annotation*
-keepclassmembers class * {
    native <methods>;
}

-keep class com.aerocine.camera.model.** { *; }
-keep class com.aerocine.camera.ui.** { *; }
-keep class com.aerocine.camera.core.** { *; }
