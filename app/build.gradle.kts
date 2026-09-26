import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun localProperty(name: String): String =
    providers.gradleProperty(name).orNull
        ?: localProperties.getProperty(name)
        ?: ""

// Follows the GitHub release tags (v3.1 → 3.1), which is what the in-app updater compares
// against. The code is derived from it so it can never fall behind: 4.0 → 40000.
val appVersionName = "4.0"
val appVersionCode = appVersionName.split(".").map { it.toInt() }.let { parts ->
    parts.getOrElse(0) { 0 } * 10_000 + parts.getOrElse(1) { 0 } * 100 + parts.getOrElse(2) { 0 }
}

// The release key, when this machine has it: path and passwords come from local.properties (or
// -P properties), never from the repository. With it both build types are signed alike, so a
// debug build installs over the published app — keeping its data — and in-app updates verify.
val releaseKeystore = localProperty("youcloud.storeFile").takeIf { it.isNotBlank() }?.let(::file)

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 34
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField(
            "String",
            "SOUNDCLOUD_CLIENT_ID",
            "\"${localProperty("soundcloud.clientId")}\""
        )
        buildConfigField(
            "String",
            "DEFAULT_SOUNDCLOUD_CLIENT_ID",
            "\"\""
        )
        buildConfigField(
            "String",
            "DEFAULT_SOUNDCLOUD_OAUTH_TOKEN",
            "\"${localProperty("soundcloud.oauthToken")}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore?.exists() == true) {
            create("youcloud") {
                storeFile = releaseKeystore
                storePassword = localProperty("youcloud.storePassword")
                keyAlias = localProperty("youcloud.keyAlias")
                keyPassword = localProperty("youcloud.keyPassword")
            }
        }
    }

    buildTypes {
        val signing = signingConfigs.findByName("youcloud") ?: signingConfigs.getByName("debug")
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signing
        }
        debug {
            signingConfig = signing
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.session)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.material.color.utilities)
    implementation(libs.newpipe.extractor)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Ships the release build as YouCloud.<version>.apk, the name the releases use since v3.1 and
// the one the in-app updater looks for first.
tasks.register("renameReleaseApk") {
    val apkDir = layout.buildDirectory.dir("outputs/apk/release")
    val apkName = "YouCloud.$appVersionName.apk"
    doLast {
        val dir = apkDir.get().asFile
        val built = File(dir, "app-release.apk")
        val renamed = File(dir, apkName)
        if (built.exists()) {
            renamed.delete()
            built.renameTo(renamed)
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy("renameReleaseApk")
}
