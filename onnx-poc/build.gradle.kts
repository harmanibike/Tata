plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.harmanibike.tata.onnxpoc"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
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
    // Isolated candidate runtime. This module is not a dependency of :app.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.32.0")
    testImplementation("junit:junit:4.13.2")
}
