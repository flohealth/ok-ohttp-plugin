val testImplementation by configurations
val testRuntimeOnly by configurations

dependencies {
    testRuntimeOnly(libs.testing.junit5.engine)

    testImplementation(libs.bundles.testing.junit5)
    testImplementation(libs.bundles.testing.mockito)
    testImplementation(libs.testing.assertj)
}
