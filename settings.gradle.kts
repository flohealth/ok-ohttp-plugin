dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // https://assertj.github.io/doc/#assertj-core-release-notes
            version("assertj", "3.24.2")
            // https://developer.android.com/studio/releases/gradle-plugin
            // Please update Lint version whenever you update Android Gradle plugin
            version("androidGradlePlugin", "7.4.2")

            // https://github.com/mannodermaus/android-junit5/releases
            version("androidJunit5", "1.9.3.0")

            // https://junit.org/junit5/docs/snapshot/release-notes/
            version("junit5", "5.9.2")

            // https://kotlinlang.org/docs/releases.html#release-details
            version("kotlin", "1.8.21")

            // https://github.com/mockito/mockito/releases
            version("mockito", "4.11.0")
            // https://github.com/mockito/mockito-kotlin/releases
            version("mockitoKotlin", "4.1.0")

            // https://github.com/square/okhttp/blob/master/CHANGELOG.md
            version("okhttp", "4.10.0")

            version("ohttp-encapsulator-android", "main-SNAPSHOT")
            version("bhttp", "main-SNAPSHOT")

            library("androidGradlePlugin", "com.android.tools.build", "gradle").versionRef("androidGradlePlugin")
            library("androidJunit5", "de.mannodermaus.gradle.plugins", "android-junit5").versionRef("androidJunit5")

            library("kotlin_gradlePlugin", "org.jetbrains.kotlin", "kotlin-gradle-plugin").versionRef("kotlin")

            library("network_okhttp", "com.squareup.okhttp3", "okhttp").versionRef("okhttp")
            library("network_okhttp_mockWebServer", "com.squareup.okhttp3", "mockwebserver").versionRef("okhttp")

            library("network_ohttpEncapsulator", "com.github.flohealth", "ohttp-encapsulator")
                .versionRef("ohttp-encapsulator-android")
            library("network_bhttp", "com.github.flohealth", "ok-bhttp")
                .versionRef("bhttp")

            library("testing_junit5", "org.junit.jupiter", "junit-jupiter-api").versionRef("junit5")
            library("testing_junit5_engine", "org.junit.jupiter", "junit-jupiter-engine").versionRef("junit5")
            library("testing_junit5_params", "org.junit.jupiter", "junit-jupiter-params").versionRef("junit5")
            bundle("testing_junit5", listOf("testing_junit5", "testing_junit5_params"))

            library("testing_mockito_core", "org.mockito", "mockito-core").versionRef("mockito")
            library("testing_mockito_inline", "org.mockito", "mockito-inline").versionRef("mockito")
            library("testing_mockito_kotlin", "org.mockito.kotlin", "mockito-kotlin").versionRef("mockitoKotlin")
            bundle(
                "testing_mockito",
                listOf(
                    "testing_mockito_core",
                    "testing_mockito_inline",
                    "testing_mockito_kotlin",
                ),
            )

            library("testing_assertj", "org.assertj", "assertj-core").versionRef("assertj")
        }
    }
}

plugins {
    id("com.gradle.enterprise").version("3.12.3")
}

include(
    ":lib-ohttp-plugin",
)
