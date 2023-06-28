import health.flo.network.gradle.PublishingConfig
import health.flo.network.gradle.setupAarPublishing
import health.flo.network.gradle.setupPublishingAndroid
import health.flo.network.gradle.setupPublishingRepositories

apply {
    from("${rootProject.projectDir}/gradle/unit-testing-android.gradle.kts")
}

plugins {
    `android-library-base`
    id("maven-publish")
}

kotlin {
    sourceSets.all {
        languageSettings {
            optIn("kotlin.contracts.ExperimentalContracts")
        }
    }
}

android {
    namespace = "health.flo.network.ohttp.client"

    setupPublishingAndroid(android = this)
}

dependencies {
    implementation(libs.network.okhttp)
    implementation(libs.network.ohttpEncapsulator)
    implementation(libs.network.bhttp)

    testImplementation(libs.network.okhttp.mockWebServer)
}

publishing {
    setupAarPublishing(
        publishing = this,
        config = PublishingConfig(
            artifactId = "ok-ohttp-plugin-android",
            version = "0.1.0",
        ),
    )
    setupPublishingRepositories(publishing = this)
}
