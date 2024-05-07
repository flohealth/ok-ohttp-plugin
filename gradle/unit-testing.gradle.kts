val testImplementation by configurations
val testRuntimeOnly by configurations

val libs = extensions.getByName("libs") as org.gradle.accessors.dm.LibrariesForLibs

dependencies {
    testRuntimeOnly(libs.testing.junit5.engine)

    testImplementation(libs.bundles.testing.junit5)
    testImplementation(libs.bundles.testing.mockito)
    testImplementation(libs.testing.assertj)
}
