import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinAndroid)
    // Only apply Google Services when the local (uncommitted) google-services.json is present.
    // This keeps CI/clean checkouts buildable without secrets.
    if (File("google-services.json").exists()) {
        alias(libs.plugins.google.services)
    }
}

configure<ApplicationExtension> {
    namespace = "fr.geoking.gaston"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.geoking.gaston"
        minSdk = 26
        targetSdk = 35
        val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        val ciRunAttempt = System.getenv("GITHUB_RUN_ATTEMPT")?.toIntOrNull() ?: 1
        val localProps = rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
            Properties().apply { file.inputStream().use { load(it) } }
        } ?: Properties()
        // Keys: local.properties first, then env (CI must set env on the step that runs Gradle, e.g. JULES_KEY, GOOGLE_MAPS_KEY)
        fun prop(key: String, default: String = "") =
            localProps.getProperty(key) ?: System.getenv(key) ?: default
        // Sanitize for Java string literal: trim, strip newlines, escape backslash and double-quote
        fun sanitizeBuildConfigString(s: String): String =
            s.trim().replace("\\", "\\\\").replace("\"", "\\\"").replace(Regex("[\r\n]+"), " ")
        val localVersionCode = prop("VERSION_CODE").takeIf { it.isNotEmpty() }?.toIntOrNull()
        val computedVersionCode = when {
            ciRunNumber != null -> (ciRunNumber * 10) + ciRunAttempt
            localVersionCode != null -> localVersionCode
            else -> 2
        }
        val computedVersionName = if (ciRunNumber != null) {
            "1.0.$ciRunNumber"
        } else {
            "1.0"
        }
        versionCode = computedVersionCode
        versionName = computedVersionName
        buildConfigField("int", "VERSION_CODE", "$computedVersionCode")
        buildConfigField("String", "VERSION_NAME", "\"$computedVersionName\"")
        val buildDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")

        val julesKey = sanitizeBuildConfigString(prop("JULES_KEY"))
        val githubToken = sanitizeBuildConfigString(prop("GITHUB_TOKEN"))
        val googleWebClientId = sanitizeBuildConfigString(prop("GOOGLE_WEB_CLIENT_ID", "your_web_client_id_placeholder"))
        val mobiliteitLuxembourgKey = sanitizeBuildConfigString(prop("MOBILITEIT_LUXEMBOURG_KEY"))
        val tomtomKey = sanitizeBuildConfigString(prop("TOMTOM_KEY"))
        val mapsApiKey = prop("GOOGLE_MAPS_KEY")
        manifestPlaceholders["googleMapsApiKey"] = mapsApiKey

        // AdMob (Play Store): defaults to Google-provided test IDs when not configured.
        // Keep these out of git by putting them in local.properties or CI env.
        val admobAppId = sanitizeBuildConfigString(
            prop("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
        )
        val admobBannerAdUnitId = sanitizeBuildConfigString(
            prop("ADMOB_BANNER_UNIT_ID", "ca-app-pub-3940256099942544/6300978111")
        )
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"$admobBannerAdUnitId\"")

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        buildConfigField("String", "JULES_KEY", "\"$julesKey\"")
        buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")
        buildConfigField("String", "MOBILITEIT_LUXEMBOURG_KEY", "\"$mobiliteitLuxembourgKey\"")
        buildConfigField("String", "TOMTOM_KEY", "\"$tomtomKey\"")

        // Required for Google Play Services Maps (references legacy Apache HTTP classes removed from Android 9+)
        useLibrary("org.apache.http.legacy")

        buildConfigField("boolean", "IS_PLAYSTORE_DISTRIBUTION", "false")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("boolean", "IS_PLAYSTORE_DISTRIBUTION", "false")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_PLAYSTORE_DISTRIBUTION", "true")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            // Android Auto: home lists every screen for DHU / car testing; release uses a shorter hub.
            buildConfigField("boolean", "AUTO_DASHBOARD_DEV_MODE", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "AUTO_DASHBOARD_DEV_MODE", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }



    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

}

// Fichier de désobscurcissement (mapping R8) associé à l’App Bundle.
// Après un bundle*Release, le mapping est copié dans build/deobfuscation/
// pour upload Play Console ou crash reporting.
afterEvaluate {
    val appExt = extensions.getByType(com.android.build.api.dsl.ApplicationExtension::class.java)
    val versionName = appExt.defaultConfig.versionName ?: "unknown"
    val copyMappings = tasks.register("copyReleaseMappings") {
        doLast {
            val buildDir = layout.buildDirectory.get().asFile
            val mappingRoot = buildDir.resolve("outputs/mapping")
            val destDir = buildDir.resolve("deobfuscation")
            if (!mappingRoot.isDirectory) return@doLast
            mappingRoot.listFiles()?.filter { it.isDirectory }?.forEach { variantDir ->
                val mappingFile = File(variantDir, "mapping.txt")
                if (mappingFile.exists()) {
                    destDir.mkdirs()
                    val dest = File(destDir, "mapping-${variantDir.name}-$versionName.txt")
                    mappingFile.copyTo(dest, overwrite = true)
                    logger.lifecycle("Mapping copié: ${dest.absolutePath}")
                }
            }
        }
    }
    tasks.matching { it.name.startsWith("bundle") && it.name.endsWith("Release") }.configureEach {
        finalizedBy(copyMappings)
    }
}

dependencies {
    implementation(project(":shared"))
    
    // Compose & Activity (lifecycle-runtime ensures LifecycleOwner is on classpath for ComponentActivity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.foundation.layout)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    debugImplementation(libs.compose.ui.tooling)

    // Android Auto
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)

    // Location (replaces deprecated LocationManager.requestSingleUpdate)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    // Maps
    implementation(libs.maps.compose)
    implementation(libs.maplibre.android)
    // Bundle Apache HTTP legacy classes for Play Services Maps Dynamite (removed from Android 9+ bootclasspath)
    implementation(libs.httpclient.android)

    // Media3 for Dashboard Tile
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.guava)

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // DI
    implementation(libs.koin.android)

    // Play In-App Update (warns when update available; flexible flow)
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)

    // Google Auth / Credentials
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.ksp)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Coil for loading API logos in About
    implementation("io.coil-kt.coil3:coil-compose:${libs.versions.coil.get()}")
    implementation("io.coil-kt.coil3:coil-network-okhttp:${libs.versions.coil.get()}")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)

    // Ads (AdMob)
    implementation(libs.play.services.ads)
}
android {
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

