import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.util.concurrent.TimeUnit

fun Project.configureRepositories(
    repositories: RepositoryHandler,
    configurations: ConfigurationContainer,
) {
    repositories.addRepositories()
    configurations.all {
        // as we use Gradle caches in pipeline caching snapshot dependencies can lead
        // to builds using stale snapshot on CI & the same unexpected behavior during local builds & debug
        resolutionStrategy {
            cacheChangingModulesFor(0, TimeUnit.SECONDS)
        }
    }
}

fun RepositoryHandler.addPluginRepositories() {
    gradlePluginPortal()
    addRepositories()
}

fun RepositoryHandler.addRepositories() {
    google()
    mavenCentral()
}
