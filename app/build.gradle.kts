import java.util.Properties

val keystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace  = "com.lightpod"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lightpod"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 2
        versionName   = "1.1"
    }

    signingConfigs {
        create("release") {
            val storePath = keystoreProps.getProperty("LIGHTPOD_STORE_FILE")
                ?: System.getenv("LIGHTPOD_STORE_FILE")
            if (storePath != null) {
                storeFile     = file(storePath)
                storePassword = keystoreProps.getProperty("LIGHTPOD_STORE_PASSWORD")
                    ?: System.getenv("LIGHTPOD_STORE_PASSWORD")
                keyAlias      = keystoreProps.getProperty("LIGHTPOD_KEY_ALIAS")
                    ?: System.getenv("LIGHTPOD_KEY_ALIAS")
                keyPassword   = keystoreProps.getProperty("LIGHTPOD_KEY_PASSWORD")
                    ?: System.getenv("LIGHTPOD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig     = signingConfigs.getByName("release")
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    implementation(libs.jaudiotagger)
}
