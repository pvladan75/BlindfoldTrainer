plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Samo zbog Flow-a u SettingsRepository — modul i dalje ne zna za Android.
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
}
