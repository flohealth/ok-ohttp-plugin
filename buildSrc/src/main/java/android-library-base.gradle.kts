import health.flo.network.gradle.Environment
import health.flo.network.gradle.configureCompiler
import health.flo.network.gradle.configureSdk

plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    configureSdk()
    configureCompiler(tasks, kotlinOptions)

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments += mapOf(
            "clearPackageData" to "true",
        )
    }

    buildFeatures {
        aidl = false
        renderScript = false
        shaders = false
    }

    kotlinOptions {
        allWarningsAsErrors = Environment.isCI
    }
}
