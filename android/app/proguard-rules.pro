# FFmpegKit registers native methods from JNI_OnLoad; release shrinking must preserve names.
-keep class com.antonkarpenko.ffmpegkit.** { *; }
-dontwarn com.antonkarpenko.ffmpegkit.**

-keep class com.arthenica.smartexception.** { *; }
-dontwarn com.arthenica.smartexception.**

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
