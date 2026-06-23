# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable <methods>; }
-keep,includedescriptorclasses class com.biliclient.**$$serializer { *; }
-keepclassmembers class com.biliclient.** {
    *** Companion;
}
-keepclasseswithmembers class com.biliclient.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class kotlin.coroutines.Continuation

# Wire / protobuf（弹幕 dm.proto；Message 子类字段不可裁）
-keep class com.squareup.wire.** { *; }
-keep class * extends com.squareup.wire.Message { *; }
-keepclassmembers class * extends com.squareup.wire.Message { *; }
-dontwarn com.squareup.wire.**

# Media3 / ExoPlayer（DASH 播放；@UnstableApi 注解类不可裁）
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep @androidx.media3.common.util.UnstableApi class * { *; }
-keepclasseswithmembers class * {
    @androidx.media3.common.util.UnstableApi <methods>;
}

# Room（实体/DAO；KSP 生成代码已编译为字节码，保留实体字段避免反射失败）
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Hilt（生成代码为具体类，通常安全；保留 @HiltViewModel 注解类）
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# ffmpeg-kit（antonkarpenko fork）：JNI native 方法 / 类名必须原样保留，
# 否则 JNI_OnLoad 的 RegisterNatives 找不到 Java 声明（AbiDetect.getNativeCpuAbi 等）而失败，
# 导致 release 版下载合流（FfmpegMuxer）崩溃。
-keep class com.antonkarpenko.ffmpegkit.** { *; }
-dontwarn com.antonkarpenko.ffmpegkit.**

# ffmpeg-kit 依赖的异常工具库（如打包则保留，未打包此规则无害）
-keep class com.arthenica.smartexception.** { *; }
-dontwarn com.arthenica.smartexception.**

# 通用兜底：保留所有 native 方法名（JNI 绑定），
# proguard-android-optimize.txt 自带的规则在 -optimize 下仍可能裁掉「Java 侧无引用」的 native 声明
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
