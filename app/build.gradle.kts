plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.keripdf"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.keripdf"
        minSdk = 23
        targetSdk = 35
        versionCode = 9
        versionName = "1.4.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
