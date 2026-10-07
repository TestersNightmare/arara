import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.ararabr.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ararabr.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val ksFile = System.getenv("KEYSTORE_FILE") ?: "release.keystore"
            val propsFile = rootProject.file("keystore.properties")
            if (System.getenv("KEYSTORE_PASSWORD") != null && rootProject.file(ksFile).exists()) {
                storeFile = rootProject.file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "arara"
                keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")
            } else if (propsFile.exists()) {
                val props = Properties().apply { load(propsFile.inputStream()) }
                val sf = props.getProperty("storeFile")
                if (!sf.isNullOrBlank() && rootProject.file(sf).exists()) {
                    storeFile = rootProject.file(sf)
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val sc = signingConfigs.getByName("release")
            if (sc.storeFile != null) {
                signingConfig = sc
            }
        }
        debug {
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Google Analytics (Firebase Analytics SDK - reads app/google-services.json)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-analytics")

    // OneSignal Push Notification SDK (App ID: 7d5dd27c-9a76-42e6-9938-e44f876b5d07)
    implementation("com.onesignal:OneSignal:[5.1.6, 5.1.99]")
}
