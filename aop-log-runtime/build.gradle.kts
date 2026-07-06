plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

group = "com.mimo.aoplog"
version = "1.0.0"

android {
    namespace = "com.mimo.aoplog.runtime"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.7.1")
}
