plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes only from env SHIFT_*; nothing secret lives in the repo.
// Read through providers so configuration cache tracks the values.
// Names must not match ApkSigningConfig members: inside create("release") { } the
// receiver's own keyPassword/keyAlias would shadow a script val of the same name.
fun env(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

val shiftKeystorePath: String? = env("SHIFT_KEYSTORE_PATH")?.let { path ->
    // Gradle does not expand "~"; a literal "~/x" would resolve inside the project dir.
    if (path == "~" || path.startsWith("~/")) System.getProperty("user.home") + path.removePrefix("~") else path
}
val shiftStorePassword: String? = env("SHIFT_KEYSTORE_PASSWORD")
val shiftKeyAlias: String? = env("SHIFT_KEY_ALIAS")
val shiftKeyPassword: String? = env("SHIFT_KEY_PASSWORD")
val hasReleaseKeystore = listOf(shiftKeystorePath, shiftStorePassword, shiftKeyAlias, shiftKeyPassword).all { it != null }

android {
    namespace = "com.walhalla.shiftalarm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.walhalla.shiftalarm"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                val keystore = File(shiftKeystorePath!!)
                require(keystore.isAbsolute && keystore.isFile) {
                    "SHIFT_KEYSTORE_PATH must point to an existing keystore file (absolute path or ~/...)"
                }
                storeFile = keystore
                storePassword = shiftStorePassword
                keyAlias = shiftKeyAlias
                keyPassword = shiftKeyPassword
                enableV1Signing = false // minSdk 24: v2 covers every supported API
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning != null) {
                signingConfig = releaseSigning
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:alarm"))
    implementation(project(":core:ui"))
    implementation(project(":feature:schedule"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:alarm"))
    implementation(project(":widget"))

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
    debugImplementation(libs.compose.ui.tooling)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.android)
}
