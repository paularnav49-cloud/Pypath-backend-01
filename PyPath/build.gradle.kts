plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
    id("app.cash.paparazzi") version "1.3.5" apply false
    // On-device CPython runtime for the Practical tab (open source, MIT): https://chaquo.com/chaquopy/
    id("com.chaquo.python") version "17.0.0" apply false
}
