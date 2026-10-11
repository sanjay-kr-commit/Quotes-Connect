import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":app:shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "connects.quotes"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "connects.quotes"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties()
    if (keystorePropsFile.exists()) {
        keystoreProps.load(FileInputStream(keystorePropsFile))
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                val storeFilePath = keystoreProps.getProperty("storeFile") ?: "release.keystore"
                storeFile = if (storeFilePath.startsWith("/")) file(storeFilePath) else rootProject.file(storeFilePath)
                storePassword = keystoreProps.getProperty("storePassword") ?: ""
                keyAlias = keystoreProps.getProperty("keyAlias") ?: "quotes-connect"
                keyPassword = keystoreProps.getProperty("keyPassword") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}