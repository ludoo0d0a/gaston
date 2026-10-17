import java.util.Properties
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

/** When false (default), only the Android target is compiled — skips JVM desktop and all iOS targets for faster builds. */
val kmpHostTargetsEnabled =
    findProperty("gaston.kmp.hostTargetsEnabled")?.toString()?.equals("true", ignoreCase = true) == true

/**
 * Keys from root `local.properties` injected into Test JVM env when not already set in the process.
 * Lets integration tests (`getEnv` / `System.getenv`) read the same secrets as BuildConfig without exporting.
 */
val localPropertiesAsTestEnv: Map<String, String> =
    rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
            .entries
            .asSequence()
            .mapNotNull { e ->
                val key = e.key?.toString()?.trim().orEmpty()
                val value = e.value?.toString()?.trim().orEmpty()
                if (key.isEmpty() || value.isEmpty() || key == "sdk.dir") null else key to value
            }
            .toMap()
    } ?: emptyMap()

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeCompiler)
    id("org.jetbrains.compose")
    `maven-publish`
}

kotlin {
    @Suppress("DEPRECATION")
    androidLibrary {
        namespace = "fr.geoking.gaston.shared"
        compileSdk = 37
        minSdk = 26

        // Creates androidHostTest (unit tests on JVM); required for commonTest expect/actual helpers.
        withHostTest {}
    }

    if (kmpHostTargetsEnabled) {
        // JVM target for running tests on desktop
        jvm("desktop") {
            compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
            testRuns["test"].executionTask.configure {
                useJUnitPlatform()
                testLogging {
                    events("passed", "failed", "skipped")
                    showStandardStreams = true
                    exceptionFormat = TestExceptionFormat.FULL
                }
            }
        }

        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        ).forEach {
            it.compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
            it.binaries.framework {
                baseName = "Shared"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            compileOnly(libs.compose.runtime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.kermit)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktxml.core)
            // Play-only: Amazon excludes are applied via configurations below (KMP
            // implementation(notation) { } only accepts String, not catalog Providers).
            implementation(libs.revenuecat.kmp.core)
            implementation(libs.revenuecat.kmp.ui)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }

        if (kmpHostTargetsEnabled) {
            getByName("desktopMain") {
                dependencies {
                    implementation(libs.ktor.client.okhttp)
                }
            }
            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
            getByName("desktopTest") {
                dependencies {
                    implementation(libs.junit.jupiter.api)
                    runtimeOnly(libs.junit.jupiter.engine)
                }
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }

        commonTest.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
        }
    }
}

// Drop Amazon Appstore IAP brought in transitively by RevenueCat (R8 stack-map noise on Play builds).
configurations.configureEach {
    exclude(group = "com.revenuecat.purchases", module = "purchases-store-amazon")
    exclude(group = "com.amazon.device", module = "amazon-appstore-sdk")
}

dependencies {
    // AGP 9.4 lint-checks InferredThreadDetector uses PersistentMap but does not ship
    // kotlinx-collections-immutable on androidLintTool — without it lintAnalyze crashes.
    "androidLintTool"("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
}

// Configure publishing to avoid archives configuration deprecation
// Note: The Kotlin Multiplatform plugin still uses 'archives' internally.
// This warning will be resolved when the plugin is updated for full Gradle 9 compatibility.
publishing {
    publications {
        withType<MavenPublication>().configureEach {
            // Use default publications created by Kotlin Multiplatform
        }
    }
}

// Classes matching *RealApiTests* hit live HTTP APIs (keys, quota, network). Exclude from default
// shared test runs (androidHostTest / desktop / iOS) so CI stays reliable. Opt in with
// RUN_REAL_API_TESTS=1 or -PrunRealApiTests=true.
val runRealApiTests: Boolean =
    System.getenv("RUN_REAL_API_TESTS")?.let { v ->
        v.equals("1", ignoreCase = true) || v.equals("true", ignoreCase = true)
    } == true ||
        (findProperty("runRealApiTests") as? String)?.equals("true", ignoreCase = true) == true

// Configure Android test tasks and optional real-API exclusions for KMP host tests
tasks.withType<Test>().configureEach {
    // Process env wins (CI secrets); fill gaps from local.properties for local real-API runs.
    localPropertiesAsTestEnv.forEach { (key, value) ->
        if (System.getenv(key).isNullOrBlank()) {
            environment(key, value)
        }
    }
    if (name.contains("UnitTest", ignoreCase = true)) {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
            showStandardStreams = true
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
    if (!runRealApiTests) {
        val isSharedKmpHostTest =
            name == "desktopTest" ||
                (name.startsWith("ios") && name.endsWith("Test")) ||
                (project.name == "shared" && name.contains("HostTest", ignoreCase = true))
        if (isSharedKmpHostTest) {
            // Full suite: keep RealApi out of default CI/local runs. If the user/IDE passes
            // --tests matching *RealApiTests*, do not exclude (otherwise "No tests found").
            val explicitRealApi =
                gradle.startParameter.taskRequests
                    .asSequence()
                    .flatMap { it.args.asSequence() }
                    .any { arg -> arg.contains("RealApiTests", ignoreCase = true) }
            if (!explicitRealApi) {
                filter.excludeTestsMatching("*RealApiTests*")
            }
        }
    }
}
