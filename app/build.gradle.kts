plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.android.sound.helper"
    compileSdk = 35 // Gunakan 35 untuk stabilitas terbaik saat ini

    defaultConfig {
        applicationId = "com.android.sound.helper"
        minSdk = 30
        targetSdk = 35 
        versionCode = 1
        versionName = "1.1"

        ndkVersion = "27.0.12077973" 

        externalNativeBuild {
            cmake {
                // Pakai C++20 karena kamu di CachyOS (modern), biar makin efisien!
                arguments("-DANDROID_STL=c++_shared", "-DCMAKE_CXX_STANDARD=20")
            }
        }
        
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            // Logika signing kamu sudah bagus untuk CI/CD
            val keystoreFile = file("release-key.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Tambahkan ini agar saat testing di lokal/CachyOS lebih cepat
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

// Perbaikan bagian Kotlin agar tidak error
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
}