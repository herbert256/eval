# Keep line numbers for useful stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Reflection-annotated attributes
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Kotlin metadata (required for reflection / coroutines / data classes)
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

# Gson — all serialized data classes must be kept so reflection finds the fields
-keep class com.eval.data.** { *; }
-keep class com.eval.ui.GameUiState { *; }
-keep class com.eval.ui.MoveDetails { *; }
-keep class com.eval.ui.MoveScore { *; }
-keep class com.eval.ui.AnalysedGame { *; }
-keep class com.eval.ui.AiPromptEntry { *; }
-keep class com.eval.ui.AiInstructionEntry { *; }
-keep class com.eval.ui.AiReportSelection { *; }
-keep class com.eval.ui.SettingsSnapshotV5 { *; }
-keep class com.eval.ui.StockfishSettings { *; }
-keep class com.eval.ui.BoardLayoutSettings { *; }
-keep class com.eval.ui.GraphSettings { *; }
-keep class com.eval.ui.GeneralSettings { *; }
-keep class com.eval.ui.AnalysisStage { *; }
-keep class com.eval.ui.ArrowMode { *; }
-keep class com.eval.ui.PlayerBarMode { *; }
-keep class com.eval.ui.EvalBarPosition { *; }
-keep class com.eval.ui.MoveQuality { *; }
-keep class com.eval.chess.** { *; }
-keep class com.eval.stockfish.** { *; }

# Gson internals reflection
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
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
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Compose keeps most things via its own rules; keep our composables readable in stacks
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
