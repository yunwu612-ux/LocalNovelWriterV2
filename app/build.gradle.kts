plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.localnovelwriter.app"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("SIGNING_KEYSTORE_PATH")
            val storePasswordEnv = System.getenv("SIGNING_STORE_PASSWORD")
            val keyPasswordEnv = System.getenv("SIGNING_KEY_PASSWORD")
            if (!storeFilePath.isNullOrBlank() && !storePasswordEnv.isNullOrBlank() && !keyPasswordEnv.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = storePasswordEnv
                keyAlias = "localnovelwriter"
                keyPassword = keyPasswordEnv
            }
        }
    }

    defaultConfig {
        applicationId = "com.localnovelwriter.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "2.6.1"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
    implementation("androidx.compose.foundation:foundation:1.7.8")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.8")
}
