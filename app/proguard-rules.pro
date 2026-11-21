# ==========================================
# Doctor List BD — Production R8/ProGuard Rules
# Multi-Network Ad Configuration:
#   ONLINE:  Unity Ads + Start.io Ads + Wortise Ads + Custom Direct Sponsor Ads
#   OFFLINE: Custom Direct Sponsor Ads ONLY (Cached)
# ==========================================

# 1. Room Entities & DAOs
-keep class com.example.data.local.** { *; }
-keep class com.example.data.model.** { *; }

# 2. Start.io SDK
-keep class com.startapp.** { *; }
-dontwarn com.startapp.**
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod

# 3. Unity Ads SDK
-keep class com.unity3d.** { *; }
-dontwarn com.unity3d.**
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 4. Wortise Android SDK (Core + Google Mobile Ads Native Runtime + Retrofit/Serialization)
-keep class com.wortise.** { *; }
-dontwarn com.wortise.**
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
-keep class kotlinx.serialization.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# 5. Google Advertising ID & Coroutines (for Production Release Builds)
-keep class com.google.android.gms.ads.identifier.** { *; }
-dontwarn com.google.android.gms.ads.identifier.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn java.lang.invoke.StringConcatFactory

# 6. Serializable & Reflection Safety
-keepclassmembers class * implements java.io.Serializable {
    static long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <methods>;
}
