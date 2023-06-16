apply {
    from("${rootProject.projectDir}/gradle/unit-testing.gradle.kts")
    plugin("de.mannodermaus.android-junit5")
}
