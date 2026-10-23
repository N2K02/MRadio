plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// App version with one decimal place. CI passes the next number (-PappVersion=1.1);
// local builds default to 1.0.
val appVersion = (findProperty("appVersion") as String?) ?: "1.0"
require(Regex("""\d+\.\d""").matches(appVersion)) { "appVersion must look like 1.0, got $appVersion" }
val appVersionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull()
    ?: appVersion.split(".").let { (major, minor) -> major.toInt() * 10 + minor.toInt() }
val appVersionSuffix = (findProperty("appVersionSuffix") as String?) ?: ""

android {
    namespace = "com.mradio.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mradio.app"
        minSdk = 26
        targetSdk = 35
        // One decimal place: 1.0, 1.1 … 1.9, 2.0. versionCode 1.4 -> 14 (always goes up).
        versionName = appVersion + appVersionSuffix
        versionCode = appVersionCode
    }


    buildTypes {
        getByName("release") {
            // R8 removes unused code (most of Compose and the icon set) and resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Left unsigned here: CI signs it with the private key (see .github/workflows/build.yml).
        }
    }

    // Compile the optional shared-updater adapter only in explicit integration builds.
    sourceSets.getByName("main").java {
        if (providers.gradleProperty("useSharedUpdater").orNull == "true") {
            srcDir("src/sharedUpdater/java")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Off by default until the private package and CI read-only credentials are ready.
    if (providers.gradleProperty("useSharedUpdater").orNull == "true") {
        implementation("dev.n2k02:updater:0.1.10")
    }
    // Material 3 Expressive (MaterialExpressiveTheme, expressive shapes/motion/components)
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.graphics:graphics-shapes:1.0.1")
    implementation("androidx.activity:activity-compose:1.11.0")

    // Playback + MediaSession (notification, lock screen, Bluetooth, Android Auto)
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")

    // Station logos (memory + disk cache)
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
}
