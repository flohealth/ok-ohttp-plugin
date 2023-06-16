package health.flo.network.gradle

import com.android.build.gradle.BaseExtension
import org.gradle.api.JavaVersion
import org.gradle.api.tasks.TaskContainer
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

data class CompilerConfig(
    val javaVersion: JavaVersion,
    val jvmTarget: JvmTarget = JvmTarget.fromTarget(javaVersion.toString()),
) {
    val javaVersionString: String = javaVersion.toString()
}

private val defaultSdkConfig = CompilerConfig(
    javaVersion = JavaVersion.VERSION_1_8,
)

fun BaseExtension.configureCompiler(
    taskContainer: TaskContainer,
    kotlinOptions: KotlinJvmOptions,
    config: CompilerConfig = defaultSdkConfig,
) {
    compileOptions {
        sourceCompatibility = config.javaVersion
        targetCompatibility = config.javaVersion
    }

    kotlinOptions.jvmTarget = config.javaVersionString

    taskContainer.withType(KotlinCompile::class.java).configureEach {
        compilerOptions {
            jvmTarget.set(config.jvmTarget)
        }
    }
}
