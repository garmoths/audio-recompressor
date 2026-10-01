# Room entity and generated database metadata.
-keep class * extends androidx.room.RoomDatabase { *; }

# FFmpegKit Java/JNI entry points are referenced by the bundled native libraries.
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**
-keep class com.arthenica.smartexception.** { *; }
-dontwarn com.arthenica.smartexception.**

# Preserve model names used in service diagnostics.
-keepattributes *Annotation*,InnerClasses,EnclosingMethod
