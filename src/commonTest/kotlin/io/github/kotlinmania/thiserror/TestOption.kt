// port-lint: source tests/test_option.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TestOption {
    private data class AnyError(val message: String) : Error {
        override fun toString(): String = message
    }

    private data class OptSourceNoBacktrace(val sourceError: Error?) : Error {
        override fun source(): Error? = sourceError
    }

    private data class OptSourceAlwaysBacktrace(
        val sourceError: Error?,
        val backtrace: Backtrace,
    ) : Error {
        override fun source(): Error? = sourceError

        override fun provide(request: Request) {
            super.provide(request)
            request.provideBacktrace(backtrace)
        }
    }

    private data class NoSourceOptBacktrace(val backtrace: Backtrace?) : Error {
        override fun provide(request: Request) {
            if (backtrace != null) {
                request.provideBacktrace(backtrace)
            }
        }
    }

    private data class AlwaysSourceOptBacktrace(
        val sourceError: Error,
        val backtrace: Backtrace?,
    ) : Error {
        override fun source(): Error = sourceError

        override fun provide(request: Request) {
            super.provide(request)
            if (backtrace != null) {
                request.provideBacktrace(backtrace)
            }
        }
    }

    private data class OptSourceOptBacktrace(
        val sourceError: Error?,
        val backtrace: Backtrace?,
    ) : Error {
        override fun source(): Error? = sourceError

        override fun provide(request: Request) {
            super.provide(request)
            if (backtrace != null) {
                request.provideBacktrace(backtrace)
            }
        }
    }

    private sealed class EnumOptSourceNoBacktrace : Error {
        data class Test(val sourceError: Error?) : EnumOptSourceNoBacktrace() {
            override fun source(): Error? = sourceError
        }
    }

    private sealed class EnumOptSourceAlwaysBacktrace : Error {
        data class Test(val sourceError: Error?, val backtrace: Backtrace) : EnumOptSourceAlwaysBacktrace() {
            override fun source(): Error? = sourceError

            override fun provide(request: Request) {
                super.provide(request)
                request.provideBacktrace(backtrace)
            }
        }
    }

    private sealed class EnumNoSourceOptBacktrace : Error {
        data class Test(val backtrace: Backtrace?) : EnumNoSourceOptBacktrace() {
            override fun provide(request: Request) {
                if (backtrace != null) {
                    request.provideBacktrace(backtrace)
                }
            }
        }
    }

    private sealed class EnumAlwaysSourceOptBacktrace : Error {
        data class Test(val sourceError: Error, val backtrace: Backtrace?) : EnumAlwaysSourceOptBacktrace() {
            override fun source(): Error = sourceError

            override fun provide(request: Request) {
                super.provide(request)
                if (backtrace != null) {
                    request.provideBacktrace(backtrace)
                }
            }
        }
    }

    private sealed class EnumOptSourceOptBacktrace : Error {
        data class Test(val sourceError: Error?, val backtrace: Backtrace?) : EnumOptSourceOptBacktrace() {
            override fun source(): Error? = sourceError

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

    private fun requestBacktrace(error: Error): Backtrace? {
        val request = Request()
        error.provide(request)
        return request.backtrace()
    }
}
