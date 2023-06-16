package health.flo.network.gradle

import com.android.build.gradle.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register

data class PublishingConfig(
    val artifactId: String,
    val version: String,
) {
    val group: String = "health.flo.network"
}

private const val buildType = "release"

fun Project.setupPublishingAndroid(android: LibraryExtension) {
    android.publishing {
        singleVariant(buildType) {
            withSourcesJar()
        }
    }
}

fun Project.setupAarPublishing(publishing: PublishingExtension, config: PublishingConfig) {
    with(publishing) {
        publications {
            register(buildType, MavenPublication::class) {
                groupId = config.group
                artifactId = config.artifactId
                version = config.version + artifactVersionSuffix()

                afterEvaluate {
                    from(components[buildType])
                }
            }
        }
    }
}

fun Project.setupJarPublishing(publishing: PublishingExtension, config: PublishingConfig) {
    with(publishing) {
        publications {
            create(buildType, MavenPublication::class.java) {
                groupId = config.group
                artifactId = config.artifactId
                version = config.version + artifactVersionSuffix()

                afterEvaluate { from(components["java"]) }
            }
        }
    }
}

fun Project.setupPublishingRepositories(publishing: PublishingExtension) {
    with(publishing) {
        repositories {
            // ./gradlew publishReleasePublicationToMavenLocal to publish to ~/.m2

            //  ./gradlew publishReleasePublicationToMvnBuildDirRepository to publish to <module>/build/repo
            maven {
                name = "MvnBuildDir"
                url = uri("${project.buildDir}/repo")
            }
        }
    }
}

fun artifactVersionSuffix(): String {
    return if (isReleaseBranch()) "" else "-SNAPSHOT"
}
