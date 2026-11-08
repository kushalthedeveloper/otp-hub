plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.otphub.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.otphub.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 29
        versionName = "1.0.29"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
