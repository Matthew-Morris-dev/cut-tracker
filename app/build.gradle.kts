import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.matthewmorris.cuttracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.matthewmorris.cuttracker"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "0.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric's Android 16 runtime reaches into the JDK's FileDescriptor internals.
        unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
    }
    sourceSets {
        // Room migration tests read the exported schemas as assets, and Robolectric only sees the
        // app variant's assets, so they go in debug builds (never release).
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.health.connect)
    implementation(libs.work.runtime.ktx)
    implementation(libs.vico.compose.m3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
