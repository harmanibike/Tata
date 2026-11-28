plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseSigningReady = listOf(
    "TATA_SIGNING_STORE_FILE",
    "TATA_SIGNING_STORE_PASSWORD",
    "TATA_SIGNING_KEY_ALIAS",
    "TATA_SIGNING_KEY_PASSWORD"
).all { !System.getenv(it).isNullOrBlank() }

android {
    namespace = "com.harmanibike.voiceo3"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.harmanibike.voiceo3"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "2.3.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            if (releaseSigningReady) {
                storeFile = file(System.getenv("TATA_SIGNING_STORE_FILE")!!)
                storePassword = System.getenv("TATA_SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("TATA_SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("TATA_SIGNING_KEY_PASSWORD")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
}
