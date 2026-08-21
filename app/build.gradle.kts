plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.program.blindfoldtrainer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.program.blindfoldtrainer"
        minSdk = 27
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:moduleapi"))
    implementation(project(":core:designsystem"))
    // Meni nudi preuzimanje jezičkog modela — model je oprema školjke, ne modula.
    implementation(project(":core:audio"))
    implementation(project(":core:progress"))
    // Samo zbog Hilt vezivanja ProgressRepository -> Room implementacija;
    // :app ne dodiruje nijedan tip iz :core:data.
    implementation(project(":core:data"))

    // Feature moduli. Dodavanje modula ovde je jedini korak — meni i
    // navigacija se dalje generišu iz registra.
    implementation(project(":feature:geometry"))
    implementation(project(":feature:pairs"))
    implementation(project(":feature:endgame"))
    implementation(project(":feature:knightpath"))
    implementation(project(":feature:check"))
    implementation(project(":feature:recall"))
    implementation(project(":feature:followgame"))
    implementation(project(":feature:dictation"))
    implementation(project(":feature:movement"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
