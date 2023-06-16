package health.flo.test.extensions

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest

/**
 * Launches beforeEachTest function before every DynamicTest execution.
 *
 * JUnit doesn't call Before* annotated methods between DynamicTests execution.
 * This can lead to dirty state of tested objects, affected by previous test-cases.
 */
fun <T> Iterable<T>.toDynamicTests(
    displayName: ((T) -> String) = { testData -> "data=$testData" },
    beforeEachTest: () -> Unit = {},
    executable: (T) -> Unit,
): List<DynamicTest> = map { testData ->
    dynamicTest(displayName.invoke(testData)) {
        beforeEachTest()
        executable(testData)
    }
}
