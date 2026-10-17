// port-lint: source tests/test_from.rs
package io.github.kotlinmania.thiserror

import io.github.kotlinmania.anyhow.anyhow
import kotlin.test.Test
import kotlin.test.assertSame

class TestFrom {
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

    private data class IoError(
        val message: String,
    ) : StdError {
        override fun toString(): String = message
    }

    private data class ErrorStruct(
        val source: IoError,
    ) : StdError {
        override fun source(): StdError = source

        companion object {
            fun from(source: IoError): ErrorStruct = ErrorStruct(source)
        }
    }

    private data class ErrorStructOptional(
        val source: IoError?,
    ) : StdError {
        override fun source(): StdError? = source

        companion object {
            fun from(source: IoError): ErrorStructOptional = ErrorStructOptional(source)
        }
    }

    private data class ErrorTuple(
        val source: IoError,
    ) : StdError {
        override fun source(): StdError = source

        companion object {
            fun from(source: IoError): ErrorTuple = ErrorTuple(source)
        }
    }

    private data class ErrorTupleOptional(
        val source: IoError?,
    ) : StdError {
        override fun source(): StdError? = source

        companion object {
            fun from(source: IoError): ErrorTupleOptional = ErrorTupleOptional(source)
        }
    }

    private sealed class ErrorEnum : StdError {
        data class Test(
            val source: IoError,
        ) : ErrorEnum() {
            override fun source(): StdError = source
        }

        companion object {
            fun from(source: IoError): ErrorEnum = Test(source)
        }
    }

    private sealed class ErrorEnumOptional : StdError {
        data class Test(
            val source: IoError?,
        ) : ErrorEnumOptional() {
            override fun source(): StdError? = source
        }

        companion object {
            fun from(source: IoError): ErrorEnumOptional = Test(source)
        }
    }

    private sealed class Many : StdError {
        data class Any(
            val source: StdError,
        ) : Many() {
            override fun source(): StdError = source
        }

        data class Io(
            val source: IoError,
        ) : Many() {
            override fun source(): StdError = source
        }

        companion object {
            fun from(source: IoError): Many = Io(source)

            fun from(source: StdError): Many = Any(source)
        }
    }

    @Test
    fun testFrom() {
        val io = IoError("oh no!")
        val any = anyhow("any error").asStdError()

        assertSame(io, ErrorStruct.from(io).source())
        assertSame(io, ErrorStructOptional.from(io).source())
        assertSame(io, ErrorTuple.from(io).source())
        assertSame(io, ErrorTupleOptional.from(io).source())
        assertSame(io, ErrorEnum.from(io).source())
        assertSame(io, ErrorEnumOptional.from(io).source())
        assertSame(io, Many.from(io).source())
        assertSame(any, Many.from(any).source())
    }
}
