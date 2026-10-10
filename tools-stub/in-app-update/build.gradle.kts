plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "fr.geoking.tools.inappupdate"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.play.app.update)
    implementation(libs.androidx.core.ktx)
}
