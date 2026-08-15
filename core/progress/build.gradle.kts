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
    // api, a ne implementation: ko računa napredak, radi i sa SessionResult-om.
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
}
