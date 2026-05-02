import org.gradle.api.tasks.testing.logging.TestExceptionFormat

/** When false (default), only the Android target is compiled — skips JVM desktop and all iOS targets for faster builds. */
val kmpHostTargetsEnabled =
    findProperty("gaston.kmp.hostTargetsEnabled")?.toString()?.equals("true", ignoreCase = true) == true

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
        compileSdk = 36
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
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }

        if (kmpHostTargetsEnabled) {
            val desktopMain by getting {
                dependencies {
                    implementation(libs.ktor.client.okhttp)
                }
            }
            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
            val desktopTest by getting {
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
            implementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
        }
    }
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
            filter.excludeTestsMatching("*RealApiTests*")
        }
    }
}
