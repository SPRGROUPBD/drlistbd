# ==========================================
# Doctor List BD — Production R8/ProGuard Rules
# Hybrid Ad Configuration:
#   ONLINE:  Start.io Ads + Unity Ads + Direct Sponsor Ads (Zero AdMob)
#   OFFLINE: Direct Sponsor Ads ONLY (Cached)
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

# 4. Serializable & Reflection Safety
-keepclassmembers class * implements java.io.Serializable {
    static long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <methods>;
}
