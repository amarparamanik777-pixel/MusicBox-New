# MusicBox ProGuard rules (release builds are not minified by default).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.musicbox.app.**$$serializer { *; }
-keepclassmembers class com.musicbox.app.** { *** Companion; }
-keepclasseswithmembers class com.musicbox.app.** { kotlinx.serialization.KSerializer serializer(...); }
