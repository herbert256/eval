# Keep line numbers for useful stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Reflection-annotated attributes
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Gson: field names are the JSON keys of API responses, exported settings and stored games,
# so every class Gson reads or writes keeps its field names (renaming them in release breaks
# settings import/export and makes stored games unreadable after an update).
-keep class com.eval.data.** { <fields>; <init>(...); }
-keep class com.eval.ui.SettingsSnapshotV5 { <fields>; <init>(...); }
-keep class com.eval.ui.*Settings { <fields>; <init>(...); }
-keep class com.eval.ui.*StageVisibility { <fields>; <init>(...); }
-keep class com.eval.ui.AiPromptEntry { <fields>; <init>(...); }
-keep class com.eval.ui.AiInstructionEntry { <fields>; <init>(...); }
-keep class com.eval.ui.AiReportSelection { <fields>; <init>(...); }
-keep class com.eval.ui.RetrievedGamesEntry { <fields>; <init>(...); }
-keep class com.eval.ui.AnalysedGame { <fields>; <init>(...); }
-keep class com.eval.ui.MoveDetails { <fields>; <init>(...); }
-keep class com.eval.ui.MoveScore { <fields>; <init>(...); }
# Enums are written by name.
-keepclassmembers enum com.eval.** { <fields>; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# pdfbox-android references the optional JPEG 2000 decoder, which the app doesn't ship.
-dontwarn com.gemalto.jp2.JP2Decoder

# Debug and info logging is stripped from release builds (it can include player names and paths).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
