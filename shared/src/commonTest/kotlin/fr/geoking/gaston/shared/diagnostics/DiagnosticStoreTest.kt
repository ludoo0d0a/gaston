package fr.geoking.gaston.shared.diagnostics

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiagnosticStoreTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testRecordErrorDefaultsToErrorLevel() {
        val store = DiagnosticStore()
        store.recordError(500, "Internal Server Error")

        val errors = store.errorLog.value
        assertEquals(1, errors.size)
        val error = errors[0]
        assertEquals(500, error.httpCode)
        assertEquals("Internal Server Error", error.message)
        assertEquals(ErrorSeverity.ERROR, error.level)
        assertNull(error.stackTrace)
    }

    @Test
    fun testRecordCrashStoresCrashLevelAndStackTrace() {
        val store = DiagnosticStore()
        val dummyStackTrace = "java.lang.NullPointerException: test crash\n\tat fr.geoking.gaston.Test.main(Test.kt:10)"
        store.recordCrash("NullPointerException encountered", dummyStackTrace)

        val errors = store.errorLog.value
        assertEquals(1, errors.size)
        val crash = errors[0]
        assertNull(crash.httpCode)
        assertEquals("NullPointerException encountered", crash.message)
        assertEquals(ErrorSeverity.CRASH, crash.level)
        assertEquals(dummyStackTrace, crash.stackTrace)
    }

    @Test
    fun testRecordWarningStoresWarningLevel() {
        val store = DiagnosticStore()
        store.recordWarning("Slow network response")

        val errors = store.errorLog.value
        assertEquals(1, errors.size)
        val warning = errors[0]
        assertNull(warning.httpCode)
        assertEquals("Slow network response", warning.message)
        assertEquals(ErrorSeverity.WARNING, warning.level)
    }

    @Test
    fun testDetailedErrorSerializationAndDeserialization() {
        val original = listOf(
            DetailedError(
                httpCode = null,
                message = "Crash message",
                timestamp = 1000L,
                level = ErrorSeverity.CRASH,
                stackTrace = "Stacktrace line 1\nStacktrace line 2"
            ),
            DetailedError(
                httpCode = 404,
                message = "Not Found",
                timestamp = 2000L,
                level = ErrorSeverity.ERROR,
                stackTrace = null
            ),
            DetailedError(
                httpCode = null,
                message = "Low memory warning",
                timestamp = 3000L,
                level = ErrorSeverity.WARNING,
                stackTrace = null
            )
        )

        val serialized = json.encodeToString(ListSerializer(DetailedError.serializer()), original)
        val deserialized = json.decodeFromString(ListSerializer(DetailedError.serializer()), serialized)

        assertEquals(3, deserialized.size)
        assertEquals(ErrorSeverity.CRASH, deserialized[0].level)
        assertEquals("Stacktrace line 1\nStacktrace line 2", deserialized[0].stackTrace)
        assertEquals(ErrorSeverity.ERROR, deserialized[1].level)
        assertEquals(404, deserialized[1].httpCode)
        assertEquals(ErrorSeverity.WARNING, deserialized[2].level)
    }

    @Test
    fun testMaxErrorLogEnforcement() {
        val store = DiagnosticStore()
        for (i in 1..60) {
            store.recordError(null, "Error #$i")
        }
        val errors = store.errorLog.value
        assertEquals(50, errors.size)
        assertEquals("Error #60", errors.first().message)
    }
}
