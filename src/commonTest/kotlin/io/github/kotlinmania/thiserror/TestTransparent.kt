// port-lint: source tests/test_transparent.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TestTransparent {
    private data class MessageError(val message: String, private val cause: StdError? = null) : StdError {
        override fun source(): StdError? = cause

        override fun toString(): String = message
    }

    private data class ContextError(val message: String, private val cause: StdError) : StdError {
        override fun source(): StdError = cause

        override fun toString(): String = message
    }

    @Test
    fun testTransparentStruct() {
        class ErrorKindE0 : StdError {
            override fun toString(): String = "E0"
        }
        data class ErrorKindE1(val io: StdError) : StdError {
            override fun source(): StdError = io

            override fun toString(): String = "E1"
        }
        data class TransparentError(val inner: StdError) : StdError {
            override fun source(): StdError? = inner.source()

            override fun toString(): String = inner.toString()
        }

        val e0 = TransparentError(ErrorKindE0())
        assertEquals("E0", e0.toString())
        assertEquals(null, e0.source())

        val io = MessageError("oh no!")
        val e1 = TransparentError(ErrorKindE1(io))
        assertEquals("E1", e1.toString())
        assertSame(io, e1.source())
    }

    @Test
    fun testTransparentEnum() {
        class TransparentEnumThis : StdError {
            override fun toString(): String = "this failed"
        }
        data class TransparentEnumOther(val inner: StdError) : StdError {
            override fun source(): StdError? = inner.source()

            override fun toString(): String = inner.toString()
        }

        assertEquals("this failed", TransparentEnumThis().toString())

        val inner = MessageError("inner")
        val outer = ContextError("outer", inner)
        val error = TransparentEnumOther(outer)
        assertEquals("outer", error.toString())
        assertEquals("inner", error.source().toString())
    }

    @Test
    fun testTransparentEnumWithDefaultMessage() {
        data class TransparentDefaultThis(val first: Int, val second: Int) : StdError {
            override fun toString(): String = "this failed: ${first}_$second"
        }
        data class TransparentDefaultOther(val inner: StdError) : StdError {
            override fun source(): StdError? = inner.source()

            override fun toString(): String = inner.toString()
        }

        assertEquals("this failed: -1_-1", TransparentDefaultThis(-1, -1).toString())

        val inner = MessageError("inner")
        val outer = ContextError("outer", inner)
        val error = TransparentDefaultOther(outer)
        assertEquals("outer", error.toString())
        assertEquals("inner", error.source().toString())
    }

    @Test
    fun testAnyhow() {
        data class AnyError(val inner: StdError) : StdError {
            override fun source(): StdError? = inner.source()

            override fun toString(): String = inner.toString()
        }

        val inner = MessageError("inner")
        val outer = ContextError("outer", inner)
        val error = AnyError(outer)
        assertEquals("outer", error.toString())
        assertEquals("inner", error.source().toString())
    }

    @Test
    fun testNonStatic() {
        data class UnexpectedErrorKind(val token: String) : StdError {
            override fun toString(): String = "unexpected token: \"$token\""
        }
        data class TransparentError(val inner: StdError) : StdError {
            override fun source(): StdError? = inner.source()

            override fun toString(): String = inner.toString()
        }

        val error = TransparentError(UnexpectedErrorKind("error"))
        assertEquals("unexpected token: \"error\"", error.toString())
        assertEquals(null, error.source())
    }
}
