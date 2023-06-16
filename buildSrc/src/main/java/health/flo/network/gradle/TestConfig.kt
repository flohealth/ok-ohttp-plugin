package health.flo.network.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.named

fun Project.setupTestsJava() {
    tasks.named<Test>("test") {
        useJUnitPlatform()

        testLogging {
            events(TestLogEvent.FAILED)

            showExceptions = true
            exceptionFormat = TestExceptionFormat.FULL
            showCauses = true
            showStackTraces = true
        }
    }
}
