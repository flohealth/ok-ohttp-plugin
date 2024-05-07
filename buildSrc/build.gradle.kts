plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    // android gradle plugin, required by custom plugin
    implementation("com.android.tools.build:gradle:8.2.2")
    // kotlin plugin, required by custom plugin
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.21")
}
