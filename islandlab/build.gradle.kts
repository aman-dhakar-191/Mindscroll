plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Island Lab: a tiny separate app (own package, no Firebase) for finding out what the phone's own
// island or Live Update chip does with a notification. Released by the "Island Lab" workflow.
val labVersionName = (findProperty("versionName") as String?) ?: "0.0.1"
val labVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1

android {
    namespace = "io.github.amandhakar.islandlab"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.amandhakar.islandlab"
        minSdk = 26
        targetSdk = 36
        versionCode = labVersionCode
        versionName = labVersionName
    }

    signingConfigs {
        val keystore = System.getenv("KEYSTORE_FILE")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
