# Julius – Règles ProGuard/R8 pour le build release
# Fichier de désobscurcissement : build/outputs/mapping/<variant>/mapping.txt
# Copie automatique après bundle : build/deobfuscation/mapping-<variant>-<version>.txt

# ---------------------------------------------------------------------------
# Kotlin
# ---------------------------------------------------------------------------
-dontwarn kotlin.**
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.Annotations**
-keep,includedescriptorclasses class fr.geoking.julius.**$$serializer { *; }
-keepclassmembers class fr.geoking.julius.** {
    *** Companion;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ---------------------------------------------------------------------------
# Koin (injection de dépendances par réflexion)
# ---------------------------------------------------------------------------
-keep class org.koin.** { *; }
-keepclassmembers class org.koin.** { *; }
-keep class fr.geoking.julius.di.** { *; }
-keepclassmembers class fr.geoking.julius.di.** { <init>(...); }
# Preserve constructors and types used by Koin for DI
-keepclassmembers class fr.geoking.julius.feature.auth.GoogleAuthManager { <init>(...); }
-keepclassmembers class fr.geoking.julius.SettingsManager { <init>(...); }
-keep class fr.geoking.julius.shared.ConversationStore { *; }
-keep class fr.geoking.julius.shared.VoiceManager { *; }
-keep class fr.geoking.julius.shared.PermissionManager { *; }
-keep class fr.geoking.julius.shared.ActionExecutor { *; }
-keep class fr.geoking.julius.agents.ConversationalAgent { *; }
-keep class fr.geoking.julius.agents.AgentResponse { *; }

# ---------------------------------------------------------------------------
# Ktor (client HTTP, sérialisation JSON)
# ---------------------------------------------------------------------------
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class kotlinx.serialization.json.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ---------------------------------------------------------------------------
# Application & entry points
# ---------------------------------------------------------------------------
-keep class fr.geoking.julius.MainActivity { *; }
-keep class fr.geoking.julius.**.*Activity { *; }
-keep class fr.geoking.julius.**.*Service { *; }
-keep class fr.geoking.julius.auto.** { *; }

# BuildConfig
-keep class fr.geoking.julius.BuildConfig { *; }

# ---------------------------------------------------------------------------
# Android Auto (Car App Library)
# ---------------------------------------------------------------------------
-keep class androidx.car.app.** { *; }

# ---------------------------------------------------------------------------
# Vosk (reconnaissance vocale offline)
# ---------------------------------------------------------------------------
-keep class org.vosk.** { *; }
-dontwarn org.vosk.**

# ---------------------------------------------------------------------------
# Google Play Services / Maps
# ---------------------------------------------------------------------------
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.gms.internal.location.**

# ---------------------------------------------------------------------------
# Llamatik / Embedded LLM (si utilisé)
# ---------------------------------------------------------------------------
-keep class com.llamatik.** { *; }
-dontwarn com.llamatik.**

# ---------------------------------------------------------------------------
# Google AI Edge LiteRT-LM (on-device GenAI)
# ---------------------------------------------------------------------------
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# ---------------------------------------------------------------------------
# Google Auth / Credentials
# -------------------------------- =------------------------------------------
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**
