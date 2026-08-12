// port-lint: source tests/test_option.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TestOption {
    private data class AnyError(
        val message: String,
    ) : StdError {
        override fun toString(): String = message
    }

    private data class OptSourceNoBacktrace(
        val sourceError: StdError?,
    ) : StdError {
        override fun source(): StdError? = sourceError
    }

    private data class OptSourceAlwaysBacktrace(
        val sourceError: StdError?,
        val backtrace: Backtrace,
    ) : StdError {
        override fun source(): StdError? = sourceError

        override fun provide(request: Request) {
            super.provide(request)
            request.provideBacktrace(backtrace)
        }
    }

    private data class NoSourceOptBacktrace(
        val backtrace: Backtrace?,
    ) : StdError {
        override fun provide(request: Request) {
            if (backtrace != null) {
                request.provideBacktrace(backtrace)
            }
        }
    }

    private data class AlwaysSourceOptBacktrace(
        val sourceError: StdError,
        val backtrace: Backtrace?,
    ) : StdError {
        override fun source(): StdError = sourceError

        override fun provide(request: Request) {
            super.provide(request)
            if (backtrace != null) {
                request.provideBacktrace(backtrace)
            }
        }
    }

    private data class OptSourceOptBacktrace(
        val sourceError: StdError?,
        val backtrace: Backtrace?,
    ) : StdError {
        override fun source(): StdError? = sourceError

        override fun provide(request: Request) {
            super.provide(request)
            if (backtrace != null) {
                request.provideBacktrace(backtrace)
            }
        }
    }

    private sealed class EnumOptSourceNoBacktrace : StdError {
        data class Test(
            val sourceError: StdError?,
        ) : EnumOptSourceNoBacktrace() {
            override fun source(): StdError? = sourceError
        }
    }

    private sealed class EnumOptSourceAlwaysBacktrace : StdError {
        data class Test(
            val sourceError: StdError?,
            val backtrace: Backtrace,
        ) : EnumOptSourceAlwaysBacktrace() {
            override fun source(): StdError? = sourceError

            override fun provide(request: Request) {
                super.provide(request)
                request.provideBacktrace(backtrace)
            }
        }
    }

    private sealed class EnumNoSourceOptBacktrace : StdError {
        data class Test(
            val backtrace: Backtrace?,
        ) : EnumNoSourceOptBacktrace() {
            override fun provide(request: Request) {
                if (backtrace != null) {
                    request.provideBacktrace(backtrace)
                }
            }
        }
    }

    private sealed class EnumAlwaysSourceOptBacktrace : StdError {
        data class Test(
            val sourceError: StdError,
            val backtrace: Backtrace?,
        ) : EnumAlwaysSourceOptBacktrace() {
            override fun source(): StdError = sourceError

            override fun provide(request: Request) {
                super.provide(request)
                if (backtrace != null) {
                    request.provideBacktrace(backtrace)
                }
            }
        }
    }

    private sealed class EnumOptSourceOptBacktrace : StdError {
        data class Test(
            val sourceError: StdError?,
            val backtrace: Backtrace?,
        ) : EnumOptSourceOptBacktrace() {
            override fun source(): StdError? = sourceError

            override fun provide(request: Request) {
                super.provide(request)
                if (backtrace != null) {
                    request.provideBacktrace(backtrace)
                }
            }
        }
    }

    @Test
    fun testOption() {
        val source = AnyError("source")
        val backtrace = Backtrace("frame")

        assertSame(source, OptSourceNoBacktrace(source).source())
        assertEquals(null, OptSourceNoBacktrace(null).source())

        assertSame(source, OptSourceAlwaysBacktrace(source, backtrace).source())
        assertSame(backtrace, requestBacktrace(OptSourceAlwaysBacktrace(source, backtrace)))

        assertSame(backtrace, requestBacktrace(NoSourceOptBacktrace(backtrace)))
        assertEquals(null, requestBacktrace(NoSourceOptBacktrace(null)))

        assertSame(source, AlwaysSourceOptBacktrace(source, backtrace).source())
        assertSame(backtrace, requestBacktrace(AlwaysSourceOptBacktrace(source, backtrace)))

        assertSame(source, OptSourceOptBacktrace(source, backtrace).source())
        assertSame(backtrace, requestBacktrace(OptSourceOptBacktrace(source, backtrace)))

        assertSame(source, EnumOptSourceNoBacktrace.Test(source).source())
        assertSame(backtrace, requestBacktrace(EnumOptSourceAlwaysBacktrace.Test(source, backtrace)))
        assertSame(backtrace, requestBacktrace(EnumNoSourceOptBacktrace.Test(backtrace)))
        assertSame(source, EnumAlwaysSourceOptBacktrace.Test(source, backtrace).source())
        assertSame(backtrace, requestBacktrace(EnumOptSourceOptBacktrace.Test(source, backtrace)))
    }

    private fun requestBacktrace(error: StdError): Backtrace? {
        val request = Request()
        error.provide(request)
        return request.backtrace()
    }
}
