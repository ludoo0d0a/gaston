pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            content {
                includeGroupAndSubgroups("com.mapbox")
            }
        }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
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
    ?: error("geoking-tools not found; clone as sibling (or path/geoking-tools), set GK_TOOLS, or checkout in CI")

includeBuild("$gkToolsRoot/android") {
    dependencySubstitution {
        substitute(module("fr.geoking.tools:debug-bar")).using(project(":debug-bar"))
    }
}
