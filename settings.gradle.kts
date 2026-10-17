pluginManagement {
    repositories {
        google()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            content {
                includeGroupAndSubgroups("com.mapbox")
            }
        }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        mavenLocal()
    }
}

rootProject.name = "Gaston"
include(":androidApp")
include(":shared")

val gkToolsRoot = System.getenv("GK_TOOLS")
    ?: listOf("geoking-tools", "../geoking-tools", "../../geoking-tools")
        .map { rootDir.resolve(it) }
        .firstOrNull { it.resolve("android").isDirectory }
        ?.absolutePath

if (gkToolsRoot != null) {
    // Same-build include (not includeBuild): Android Studio sync fails to map composite
    // Android library modules ("Expected android module not found! … Path: :debug-bar").
    val toolsAndroid = file(gkToolsRoot).resolve("android")
    include(":debug-bar")
    project(":debug-bar").projectDir = toolsAndroid.resolve("debug-bar")
    include(":in-app-update")
    project(":in-app-update").projectDir = toolsAndroid.resolve("in-app-update")
} else {
    include(":tools-stub:debug-bar")
    include(":tools-stub:in-app-update")
}
