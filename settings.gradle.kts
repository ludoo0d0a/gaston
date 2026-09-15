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
