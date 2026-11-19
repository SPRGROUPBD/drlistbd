# ==========================================
# Doctor List BD — Production R8/ProGuard Rules
# ==========================================

# 1. Start.io (StartApp) In-App SDK
-keep class com.startapp.** { *; }
-dontwarn com.startapp.**

# Preserve WebView JavaScript interfaces & reflection metadata used by Start.io ad templates
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers class * extends android.webkit.WebView {
    public *;
}

# 2. Google Play Services Ads Identifier (GAID for real production ads)
-keep class com.google.android.gms.ads.identifier.** { *; }
-dontwarn com.google.android.gms.ads.identifier.**
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.common.**

# 3. Room Entities & DAOs
-keep class com.example.data.local.** { *; }
-keep class com.example.data.model.** { *; }

# 4. Serializable & Reflection Safety
-keepclassmembers class * implements java.io.Serializable {
    static long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <methods>;
}
