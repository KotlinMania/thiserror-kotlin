// port-lint: source tests/test_transparent.rs
package io.github.kotlinmania.thiserror

import io.github.kotlinmania.anyhow.anyhow
import io.github.kotlinmania.anyhow.context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class TestTransparent {
    private fun io.github.kotlinmania.anyhow.Error.asStdError(): StdError =
        object : StdError {
            override fun source(): StdError? =
                this@asStdError.source()?.let { s ->
                    object : StdError {
                        override fun toString(): String = s.toString()
                    }
                }

            override fun toString(): String = this@asStdError.toString()
        }

    private data class ErrorKindE1(
        val io: StdError,
    ) : StdError {
        override fun source(): StdError = io

        override fun toString(): String = "E1"
    }

    private data class TransparentError(
        val inner: StdError,
    ) : StdError {
        override fun source(): StdError? = inner.source()

        override fun toString(): String = inner.toString()
    }

    private class TransparentEnumThis : StdError {
        override fun toString(): String = "this failed"
    }

    private data class TransparentEnumOther(
        val inner: StdError,
    ) : StdError {
        override fun source(): StdError? = inner.source()

        override fun toString(): String = inner.toString()
    }

    private data class TransparentDefaultThis(
        val first: Int,
        val second: Int,
    ) : StdError {
        override fun toString(): String = "this failed: ${first}_$second"
    }

    private data class TransparentDefaultOther(
        val inner: StdError,
    ) : StdError {
        override fun source(): StdError? = inner.source()

        override fun toString(): String = inner.toString()
    }

    private data class Any(
        val inner: StdError,
    ) : StdError {
        override fun source(): StdError? = inner.source()

        override fun toString(): String = inner.toString()

        companion object {
            fun from(error: StdError): Any = Any(error)
        }
    }

    @Test
    fun testTransparentStruct() {
        class ErrorKindE0 : StdError {
            override fun toString(): String = "E0"
        }

        data class IoError(
            val message: String,
        ) : StdError {
            override fun toString(): String = message
        }

        val e0 = TransparentError(ErrorKindE0())
        assertEquals("E0", e0.toString())
        assertNull(e0.source())

        val io = IoError("oh no!")
        val e1 = TransparentError(ErrorKindE1(io))
        assertEquals("E1", e1.toString())
        assertSame(io, e1.source())
    }

    @Test
    fun testTransparentEnum() {
        assertEquals("this failed", TransparentEnumThis().toString())

        val error = TransparentEnumOther(anyhow("inner").context("outer").asStdError())
        assertEquals("outer", error.toString())
        assertEquals("inner", error.source()?.toString())
    }

    @Test
    fun testTransparentEnumWithDefaultMessage() {
        assertEquals("this failed: -1_-1", TransparentDefaultThis(-1, -1).toString())

        val error = TransparentDefaultOther(anyhow("inner").context("outer").asStdError())
        assertEquals("outer", error.toString())
        assertEquals("inner", error.source()?.toString())
    }

    @Test
    fun testAnyhow() {
        val error = Any.from(anyhow("inner").context("outer").asStdError())
        assertEquals("outer", error.toString())
        assertEquals("inner", error.source()?.toString())
    }

    @Test
    fun testNonStatic() {
        data class UnexpectedErrorKind(
            val token: String,
        ) : StdError {
            override fun toString(): String = "unexpected token: \"$token\""
        }

        val error = TransparentError(UnexpectedErrorKind("error"))
        assertEquals("unexpected token: \"error\"", error.toString())
        assertNull(error.source())
    }
}
