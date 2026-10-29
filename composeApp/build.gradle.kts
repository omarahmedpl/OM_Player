import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import org.gradle.api.tasks.Exec
import com.android.build.gradle.AppExtension

// Release signing comes from the git-ignored local.properties (omplayer.release.*); never from tracked files.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val ytdlGoJniLibs = layout.buildDirectory.dir("generated/ytdlGo/jniLibs")
val ytdlGoBinary = providers.gradleProperty("goBinary")
    .orElse(providers.environmentVariable("GO_BINARY"))
    .orElse("go")

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
        // Plain-JVM code used by both targets (network lookups).
        androidMain { kotlin.srcDir("src/jvmShared/kotlin") }
        desktopMain.kotlin.srcDir("src/jvmShared/kotlin")
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
        versionCode = 6
        versionName = "1.0.0"
    }
    buildFeatures { buildConfig = true }
    // Two editions. "lite" is the public one. "plus" adds the Discover tab (online music) and installs as a separate app.
    flavorDimensions += "edition"
    productFlavors {
        create("lite") {
            dimension = "edition"
            buildConfigField("boolean", "DISCOVER", "false")
            manifestPlaceholders["appLabel"] = "OM Player"
        }
        create("plus") {
            dimension = "edition"
            applicationIdSuffix = ".plus"
            versionNameSuffix = "-plus"
            buildConfigField("boolean", "DISCOVER", "true")
            manifestPlaceholders["appLabel"] = "OM Player+"
        }
    }
    sourceSets.getByName("plus").jniLibs.srcDir(ytdlGoJniLibs)
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
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
        // The Plus bridge is a packaged executable in jniLibs, so it must be extracted before launch.
        jniLibs { useLegacyPackaging = true }
    }
}

val androidSdkPath = (extensions.getByName("android") as AppExtension).sdkDirectory.absolutePath
val buildYtdlGoAndroid = tasks.register<Exec>("buildYtdlGoAndroid") {
    inputs.files(rootProject.fileTree("tools/ytdl-go"))
    outputs.dir(ytdlGoJniLibs)
    commandLine(
        "bash",
        rootProject.file("tools/ytdl-go/build-android.sh").absolutePath,
        ytdlGoJniLibs.get().asFile.absolutePath,
        ytdlGoBinary.get(),
        androidSdkPath,
    )
}

tasks.configureEach {
    if (name.startsWith("mergePlus") && (name.endsWith("NativeLibs") || name.endsWith("JniLibFolders"))) {
        dependsOn(buildYtdlGoAndroid)
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
            windows {
                iconFile.set(project.file("icons/om-player.ico"))
                menuGroup = "OM Player"
                shortcut = true
                dirChooser = true
                // Fixed id so a newer MSI upgrades an installed OM Player instead of installing next to it.
                upgradeUuid = "290412f0-595a-4f12-8f54-3ae55f2522b1"
            }
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
