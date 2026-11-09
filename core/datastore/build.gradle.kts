plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.walhalla.shiftalarm.core.datastore"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
