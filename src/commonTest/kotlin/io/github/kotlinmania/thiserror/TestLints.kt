// port-lint: source tests/test_lints.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TestLints {
    private data class AnyError(val message: String) : StdError {
        override fun toString(): String = message
    }

    private data class MyError(val sourceError: StdError? = null) : StdError {
        override fun source(): StdError? = sourceError

        override fun toString(): String = "..."
    }

    private sealed class MyLifetimeError : StdError {
        data class A(val sourceError: StdError) : MyLifetimeError() {
            override fun source(): StdError = sourceError

            override fun toString(): String = "..."
        }

        data class B(val value: Unit) : MyLifetimeError() {
            override fun toString(): String = "..."
        }
    }

    private class DeprecatedStruct : StdError {
        override fun toString(): String = "..."
    }

    private data class DeprecatedStructField(val message: String) : StdError {
        override fun toString(): String = "$message $message"
    }

    private sealed class DeprecatedEnum : StdError {
        data object Variant : DeprecatedEnum() {
            override fun toString(): String = "..."
        }
    }

    private sealed class DeprecatedVariant : StdError {
        data object Variant : DeprecatedVariant() {
            override fun toString(): String = "..."
        }
    }

    private sealed class DeprecatedFrom : StdError {
        data class Variant(val sourceError: DeprecatedStruct) : DeprecatedFrom() {
            override fun source(): StdError = sourceError

            override fun toString(): String = sourceError.toString()
        }
    }

    @Test
    fun testAllowAttributes() {
        val source = AnyError("source")
        val error = MyError(source)

        assertEquals("...", error.toString())
        assertIs<AnyError>(error.source())
    }

    @Test
    fun testUnusedQualifications() {
        assertEquals("...", MyError().toString())
    }

    @Test
    fun testNeedlessLifetimes() {
        val source = AnyError("source")
        val error = MyLifetimeError.A(source)

        assertIs<AnyError>(error.source())
        assertEquals("...", MyLifetimeError.B(Unit).toString())
    }

    @Test
    fun testForbidNeedlessLifetimes() {
        val source = AnyError("source")

        assertIs<AnyError>(MyError(source).source())
    }

    @Test
    fun testDeprecated() {
        val deprecatedStruct = DeprecatedStruct()
        val deprecatedStructField = DeprecatedStructField("...")
        val deprecatedEnum = DeprecatedEnum.Variant
        val deprecatedVariant = DeprecatedVariant.Variant
        val deprecatedFrom = DeprecatedFrom.Variant(deprecatedStruct)

        assertEquals("...", deprecatedStruct.toString())
        assertEquals("... ...", deprecatedStructField.toString())
        assertEquals("...", deprecatedEnum.toString())
        assertEquals("...", deprecatedVariant.toString())
        assertEquals("...", deprecatedFrom.toString())
        assertIs<DeprecatedStruct>(deprecatedFrom.source())
    }
}
