// port-lint: source tests/test_backtrace.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TestBacktrace {
    private class Inner : StdError {
        override fun toString(): String = "..."
    }

    private data class InnerBacktrace(val backtrace: Backtrace) : StdError {
        override fun provide(request: Request) {
            request.provideBacktrace(backtrace)
        }

        override fun toString(): String = "..."
    }

    private data class PlainBacktrace(val backtrace: Backtrace) : StdError {
        override fun provide(request: Request) {
            request.provideBacktrace(backtrace)
        }
    }

    private data class ExplicitBacktrace(val backtrace: Backtrace) : StdError {
        override fun provide(request: Request) {
            request.provideBacktrace(backtrace)
        }
    }

    private data class NotBacktrace(val backtrace: NotBacktraceValue) : StdError

    private data class NotBacktraceValue(val frames: String)

    private data class OptBacktrace(val backtrace: Backtrace?) : StdError {
        override fun provide(request: Request) {
            if (backtrace != null) {
                request.provideBacktrace(backtrace)
            }
        }
    }

    private data class ArcBacktrace(val backtrace: Backtrace) : StdError {
        override fun provide(request: Request) {
            request.provideBacktrace(backtrace)
        }
    }

    private data class BacktraceFrom(val sourceError: Inner, val backtrace: Backtrace) : StdError {
        override fun source(): StdError = sourceError

        override fun provide(request: Request) {
            super.provide(request)
            request.provideBacktrace(backtrace)
        }

        companion object {
            fun from(source: Inner): BacktraceFrom = BacktraceFrom(source, Backtrace("captured"))
        }
    }

    private data class CombinedBacktraceFrom(val sourceError: InnerBacktrace) : StdError {
        override fun source(): StdError = sourceError

        override fun provide(request: Request) {
            sourceError.provide(request)
            super.provide(request)
        }

        companion object {
            fun from(source: InnerBacktrace): CombinedBacktraceFrom = CombinedBacktraceFrom(source)
        }
    }

    private data class OptBacktraceFrom(val sourceError: Inner, val backtrace: Backtrace?) : StdError {
        override fun source(): StdError = sourceError

        override fun provide(request: Request) {
            super.provide(request)
            if (backtrace != null) {
                request.provideBacktrace(backtrace)
            }
        }

        companion object {
            fun from(source: Inner): OptBacktraceFrom = OptBacktraceFrom(source, Backtrace("captured"))
        }
    }

    private data class ArcBacktraceFrom(val sourceError: Inner, val backtrace: Backtrace) : StdError {
        override fun source(): StdError = sourceError

        override fun provide(request: Request) {
            super.provide(request)
            request.provideBacktrace(backtrace)
        }

        companion object {
            fun from(source: Inner): ArcBacktraceFrom = ArcBacktraceFrom(source, Backtrace("captured"))
        }
    }

    private data class AnyhowBacktrace(val sourceError: StdError, val backtrace: Backtrace) : StdError {
        override fun source(): StdError = sourceError

        override fun provide(request: Request) {
            super.provide(request)
            request.provideBacktrace(backtrace)
        }
    }

    private data class BoxDynErrorBacktrace(val sourceError: StdError) : StdError {
        override fun source(): StdError = sourceError

        override fun provide(request: Request) {
            sourceError.provide(request)
            super.provide(request)
        }
    }

    private sealed class EnumPlainBacktrace : StdError {
        data class Test(val backtrace: Backtrace) : EnumPlainBacktrace() {
            override fun provide(request: Request) {
                request.provideBacktrace(backtrace)
            }
        }
    }

    private sealed class EnumExplicitBacktrace : StdError {
        data class Test(val backtrace: Backtrace) : EnumExplicitBacktrace() {
            override fun provide(request: Request) {
                request.provideBacktrace(backtrace)
            }
        }
    }

    private sealed class EnumOptBacktrace : StdError {
        data class Test(val backtrace: Backtrace?) : EnumOptBacktrace() {
            override fun provide(request: Request) {
                if (backtrace != null) {
                    request.provideBacktrace(backtrace)
                }
            }
        }
    }

    private sealed class EnumArcBacktrace : StdError {
        data class Test(val backtrace: Backtrace) : EnumArcBacktrace() {
            override fun provide(request: Request) {
                request.provideBacktrace(backtrace)
            }
        }
    }

    private sealed class EnumBacktraceFrom : StdError {
        data class Test(val sourceError: Inner, val backtrace: Backtrace) : EnumBacktraceFrom() {
            override fun source(): StdError = sourceError

            override fun provide(request: Request) {
                super.provide(request)
                request.provideBacktrace(backtrace)
            }
        }

        companion object {
            fun from(source: Inner): EnumBacktraceFrom = Test(source, Backtrace("captured"))
        }
    }

    private sealed class EnumCombinedBacktraceFrom : StdError {
        data class Test(val sourceError: InnerBacktrace) : EnumCombinedBacktraceFrom() {
            override fun source(): StdError = sourceError

            override fun provide(request: Request) {
                sourceError.provide(request)
                super.provide(request)
            }
        }

        companion object {
            fun from(source: InnerBacktrace): EnumCombinedBacktraceFrom = Test(source)
        }
    }

    private sealed class EnumOptBacktraceFrom : StdError {
        data class Test(val sourceError: Inner, val backtrace: Backtrace?) : EnumOptBacktraceFrom() {
            override fun source(): StdError = sourceError

            override fun provide(request: Request) {
                super.provide(request)
                if (backtrace != null) {
                    request.provideBacktrace(backtrace)
                }
            }
        }

        companion object {
            fun from(source: Inner): EnumOptBacktraceFrom = Test(source, Backtrace("captured"))
        }
    }

    private sealed class EnumArcBacktraceFrom : StdError {
        data class Test(val sourceError: Inner, val backtrace: Backtrace) : EnumArcBacktraceFrom() {
            override fun source(): StdError = sourceError

            override fun provide(request: Request) {
                super.provide(request)
                request.provideBacktrace(backtrace)
            }
        }

        companion object {
            fun from(source: Inner): EnumArcBacktraceFrom = Test(source, Backtrace("captured"))
        }
    }

    @Test
    fun testStructBacktrace() {
        val backtrace = Backtrace("frame")
        val innerBacktrace = InnerBacktrace(backtrace)

        assertSame(backtrace, requestBacktrace(PlainBacktrace(backtrace)))
        assertSame(backtrace, requestBacktrace(ExplicitBacktrace(backtrace)))
        assertEquals(null, requestBacktrace(NotBacktrace(NotBacktraceValue("frame"))))
        assertSame(backtrace, requestBacktrace(OptBacktrace(backtrace)))
        assertSame(backtrace, requestBacktrace(ArcBacktrace(backtrace)))
        assertEquals("captured", requestBacktrace(BacktraceFrom.from(Inner()))?.frames)
        assertSame(backtrace, requestBacktrace(CombinedBacktraceFrom.from(innerBacktrace)))
        assertEquals("captured", requestBacktrace(OptBacktraceFrom.from(Inner()))?.frames)
        assertEquals("captured", requestBacktrace(ArcBacktraceFrom.from(Inner()))?.frames)
        assertSame(backtrace, requestBacktrace(AnyhowBacktrace(Inner(), backtrace)))
        assertSame(backtrace, requestBacktrace(BoxDynErrorBacktrace(PlainBacktrace(backtrace))))
    }

    @Test
    fun testEnumBacktrace() {
        val backtrace = Backtrace("frame")
        val innerBacktrace = InnerBacktrace(backtrace)

        assertSame(backtrace, requestBacktrace(EnumPlainBacktrace.Test(backtrace)))
        assertSame(backtrace, requestBacktrace(EnumExplicitBacktrace.Test(backtrace)))
        assertSame(backtrace, requestBacktrace(EnumOptBacktrace.Test(backtrace)))
        assertSame(backtrace, requestBacktrace(EnumArcBacktrace.Test(backtrace)))
        assertEquals("captured", requestBacktrace(EnumBacktraceFrom.from(Inner()))?.frames)
        assertSame(backtrace, requestBacktrace(EnumCombinedBacktraceFrom.from(innerBacktrace)))
        assertEquals("captured", requestBacktrace(EnumOptBacktraceFrom.from(Inner()))?.frames)
        assertEquals("captured", requestBacktrace(EnumArcBacktraceFrom.from(Inner()))?.frames)
    }

    @Test
    fun testBacktrace() {
        assertEquals(null, requestBacktrace(Inner()))
    }

    private fun requestBacktrace(error: StdError): Backtrace? {
        val request = Request()
        error.provide(request)
        return request.backtrace()
    }
}
