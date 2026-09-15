pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
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
        mavenCentral()
    }
}

rootProject.name = "Gaston"
include(":androidApp")
include(":shared")
