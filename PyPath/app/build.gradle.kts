plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.paparazzi")
    id("com.chaquo.python")
}

android {
    namespace = "com.pypath.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pypath.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-practical"
        vectorDrawables { useSupportLibrary = true }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Python runtime ABIs. x86/x86_64 cover emulators; arm ABIs cover real phones.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Debug signing so the release APK is installable for testing. Replace with a real keystore before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

chaquopy {
    defaultConfig {
        // Python 3.11 is the newest version Chaquopy ships for all four Android ABIs (incl. 32-bit
        // armeabi-v7a phones and x86 emulators); 3.12+ is 64-bit only.
        version = "3.11"
        // App Python sources (app/src/main/python) are bundled as .py so building does not
        // require a host Python 3.12. No third-party pip packages are used.
        pyc { src = false }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // Google Sign-In through Android Credential Manager ("Sign in with Google").
    // Google Play services does the network work, so the app adds no INTERNET permission.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")

    // On-device tests: real Python execution through PythonRunner + the Practical UI.
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
