plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "dev.dux.organizer"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.dux.organizer"
        // 33: required for USE_EXACT_ALARM and POST_NOTIFICATIONS (milestone 4).
        minSdk = 33
        targetSdk = 37
        versionCode = 27
        versionName = "0.22.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Only 64-bit ARM phones: LiteRT-LM's native libs for x86_64 (emulators) add ~26 MB.
        ndk { abiFilters += "arm64-v8a" }
    }
    // Phone-only app: no ChromeOS (x86_64) build, see abiFilters.
    lint { disable += "ChromeOsAbiSupport" }

    // Releases are installed over the previous version: every one must be signed with this same key.
    val storeFile = providers.gradleProperty("JARVIS_STORE_FILE").orNull
    if (storeFile != null) signingConfigs.create("release") {
        this.storeFile = file(storeFile)
        storePassword = providers.gradleProperty("JARVIS_STORE_PASSWORD").get()
        keyAlias = providers.gradleProperty("JARVIS_KEY_ALIAS").get()
        keyPassword = providers.gradleProperty("JARVIS_KEY_PASSWORD").get()
    }

    buildTypes {
        debug {
            // Separate app ("Jarvis Dev") next to the real one: device tests install and uninstall it
            // on the phone without touching the real app or its data (dev.dux.organizer).
            applicationIdSuffix = ".debug"
        }
        release {
            // R8 removes unused code and optimizes: Compose in debug is noticeably slower.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The release key lives outside the repo (~/.gradle/gradle.properties); CI has no key, so it
            // signs with its debug key only to check the build. APKs from CI must never be shared.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

room {
    // Exported schemas are committed: they are the reference to test every migration.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.litertlm.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
