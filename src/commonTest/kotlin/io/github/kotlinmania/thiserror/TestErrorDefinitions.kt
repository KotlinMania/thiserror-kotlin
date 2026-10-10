// port-lint: source tests/test_error.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestErrorDefinitions {
    private data class TestErrorBracedError(
        val msg: String,
        val pos: Int,
    ) : StdError {
        override fun toString(): String = "BracedError"
    }

    private data class TestErrorTupleError(
        val message: String,
        val pos: Int,
    ) : StdError {
        override fun toString(): String = "TupleError"
    }

    private class TestErrorUnitError : StdError {
        override fun toString(): String = "UnitError"
    }

    private data class TestErrorWithSource(
        val cause: StdError,
    ) : StdError {
        override fun source(): StdError = cause

        override fun toString(): String = "WithSource"
    }

    private data class TestErrorWithAnyhow(
        val cause: StdError,
    ) : StdError {
        override fun source(): StdError = cause

        override fun toString(): String = "WithAnyhow"
    }

    private sealed class TestErrorEnumError : StdError {
        data class Braced(
            val cause: StdError,
        ) : TestErrorEnumError() {
            override fun source(): StdError = cause

            override fun toString(): String = "Braced"
        }

        data class Tuple(
            val cause: StdError,
        ) : TestErrorEnumError() {
            override fun source(): StdError = cause

            override fun toString(): String = "Tuple"
        }

        data object Unit : TestErrorEnumError() {
            override fun toString(): String = "Unit"
        }
    }

    @Test
    fun testDefinitions() {
        val braced = TestErrorBracedError("error message", 42)
        assertEquals("error message", braced.msg)
        assertEquals(42, braced.pos)
        assertEquals("BracedError", braced.toString())
        assertNull(braced.source())

        val tuple = TestErrorTupleError("tuple message", 1)
        assertEquals("tuple message", tuple.message)
        assertEquals(1, tuple.pos)
        assertEquals("TupleError", tuple.toString())
        assertNull(tuple.source())

        val unit = TestErrorUnitError()
        assertEquals("UnitError", unit.toString())
        assertNull(unit.source())

        val withSource = TestErrorWithSource(unit)
        assertEquals("WithSource", withSource.toString())
        assertEquals(unit, withSource.source())

        val withAnyhow = TestErrorWithAnyhow(unit)
        assertEquals("WithAnyhow", withAnyhow.toString())
        assertEquals(unit, withAnyhow.source())

        val enumBraced = TestErrorEnumError.Braced(unit)
        assertEquals("Braced", enumBraced.toString())
        assertEquals(unit, enumBraced.source())

        val enumTuple = TestErrorEnumError.Tuple(unit)
        assertEquals("Tuple", enumTuple.toString())
        assertEquals(unit, enumTuple.source())

        val enumUnit = TestErrorEnumError.Unit
        assertEquals("Unit", enumUnit.toString())
        assertNull(enumUnit.source())
    }
}
