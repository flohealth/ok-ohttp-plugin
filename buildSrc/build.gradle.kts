plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    // android gradle plugin, required by custom plugin
    implementation(libs.androidGradlePlugin)
    // kotlin plugin, required by custom plugin
    implementation(libs.kotlin.gradlePlugin)
}
