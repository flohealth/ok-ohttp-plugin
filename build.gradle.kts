plugins {
    id("maven-publish")
}

buildscript {

    repositories.addPluginRepositories()

    dependencies {
        classpath(libs.androidGradlePlugin)
        classpath(libs.kotlin.gradlePlugin)
        classpath(libs.androidJunit5)
    }
}

allprojects {
    configureRepositories(repositories, configurations)
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
