plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.android.sound.helper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.android.sound.helper"
        minSdk = 30
        targetSdk = 30
        versionCode = 2
        versionName = "2.0"

        ndkVersion = "29.0.14206865"

        externalNativeBuild {
            cmake {
                cppFlags("-O3", "-flto", "-fvisibility=hidden", "-std=c++20", "-fno-exceptions")
                arguments("-DANDROID_STL=c++_static")
            }
        }

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable += "ExpiredTargetSdkVersion"
    }

    signingConfigs {
        create("release") {
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
}