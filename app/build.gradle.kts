plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ie.derek.snezzysdiary"
    compileSdk = 34

    defaultConfig {
        applicationId = "ie.derek.snezzysdiary"
        minSdk = 29
        targetSdk = 34
        versionCode = 14
        versionName = "1.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signs release builds with the debug key so you can install them straight away
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
}
