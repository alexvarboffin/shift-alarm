plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.walhalla.shiftalarm.widget"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.glance.appwidget)
    debugImplementation(libs.androidx.glance.preview)
    debugImplementation(libs.androidx.glance.appwidget.preview)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.android)
    testImplementation(libs.junit)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
