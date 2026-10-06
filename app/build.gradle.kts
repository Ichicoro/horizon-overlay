plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "sh.zelda.htmlfeed"
    compileSdk = 37

    defaultConfig {
        applicationId = "sh.zelda.htmlfeed"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility(libs.versions.jvmVersion.get())
        targetCompatibility(libs.versions.jvmVersion.get())
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmVersion.get().toInt())
}

dependencies {
    implementation(project(":google-gsa"))
    implementation(libs.core.ktx)
    implementation(libs.material) // Theme.Material3.* resources used by the overlay window
    implementation(libs.savedstate) // the overlay window owns its own SavedStateRegistry

    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
