import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Release signing comes from the git-ignored local.properties (omplayer.release.*); never from tracked files.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
            implementation(libs.media3.session)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.vlcj)
        }
    }
}

android {
    namespace = "com.omplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.omplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        localProps.getProperty("omplayer.release.storeFile")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = localProps.getProperty("omplayer.release.storePassword")
                keyAlias = localProps.getProperty("omplayer.release.keyAlias")
                keyPassword = localProps.getProperty("omplayer.release.keyPassword")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        // Release-like build for performance measurement on emulators/devices (debug key, not debuggable).
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

compose.desktop {
    application {
        mainClass = "com.omplayer.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "om-player"
            packageVersion = "1.0.0"
            description = "OM Player: an ad-free video player"
            vendor = "OM Player"
            // vlcj loads libVLC via JNA; ship the full runtime so no JVM module is missing at run time.
            includeAllModules = true
            linux {
                iconFile.set(project.file("icons/om-player.png"))
                debMaintainer = "0omarahmed73@gmail.com"
                menuGroup = "AudioVideo;Video;Player"
                appCategory = "video"
                shortcut = true
            }
        }
    }
}
