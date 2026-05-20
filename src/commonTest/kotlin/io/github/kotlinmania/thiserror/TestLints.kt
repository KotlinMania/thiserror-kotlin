// port-lint: source tests/test_lints.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TestLints {
    private data class AnyError(val message: String) : Error {
        override fun toString(): String = message
    }

    private data class MyError(val sourceError: Error? = null) : Error {
        override fun source(): Error? = sourceError

        override fun toString(): String = "..."
    }

    private sealed class MyLifetimeError : Error {
        data class A(val sourceError: Error) : MyLifetimeError() {
            override fun source(): Error = sourceError

            override fun toString(): String = "..."
        }

        data class B(val value: Unit) : MyLifetimeError() {
            override fun toString(): String = "..."
        }
    }

    private class DeprecatedStruct : Error {
        override fun toString(): String = "..."
    }

    private data class DeprecatedStructField(val message: String) : Error {
        override fun toString(): String = "$message $message"
    }

    private sealed class DeprecatedEnum : Error {
        data object Variant : DeprecatedEnum() {
            override fun toString(): String = "..."
        }
    }

    private sealed class DeprecatedVariant : Error {
        data object Variant : DeprecatedVariant() {
            override fun toString(): String = "..."
        }
    }

    private sealed class DeprecatedFrom : Error {
        data class Variant(val sourceError: DeprecatedStruct) : DeprecatedFrom() {
            override fun source(): Error = sourceError

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
