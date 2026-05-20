// port-lint: source tests/test_backtrace.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TestBacktrace {
    private class Inner : Error {
        override fun toString(): String = "..."
    }

    private data class InnerBacktrace(val backtrace: Backtrace) : Error {
        override fun provide(request: Request) {
            request.provideBacktrace(backtrace)
        }

        override fun toString(): String = "..."
    }

    private data class PlainBacktrace(val backtrace: Backtrace) : Error {
        override fun provide(request: Request) {
            request.provideBacktrace(backtrace)
        }
    }

    private data class ExplicitBacktrace(val backtrace: Backtrace) : Error {
        override fun provide(request: Request) {
            request.provideBacktrace(backtrace)
        }
    }

    private data class NotBacktrace(val backtrace: NotBacktraceValue) : Error

    private data class NotBacktraceValue(val frames: String)

    private data class OptBacktrace(val backtrace: Backtrace?) : Error {
        override fun provide(request: Request) {
            if (backtrace != null) {
                request.provideBacktrace(backtrace)
            }
        }
    }

    private data class ArcBacktrace(val backtrace: Backtrace) : Error {
        override fun provide(request: Request) {
            request.provideBacktrace(backtrace)
        }
    }

    private data class BacktraceFrom(val sourceError: Inner, val backtrace: Backtrace) : Error {
        override fun source(): Error = sourceError

        override fun provide(request: Request) {
            super.provide(request)
            request.provideBacktrace(backtrace)
        }

        companion object {
            fun from(source: Inner): BacktraceFrom = BacktraceFrom(source, Backtrace("captured"))
        }
    }

    private data class CombinedBacktraceFrom(val sourceError: InnerBacktrace) : Error {
        override fun source(): Error = sourceError

        override fun provide(request: Request) {
            sourceError.provide(request)
            super.provide(request)
        }

        companion object {
            fun from(source: InnerBacktrace): CombinedBacktraceFrom = CombinedBacktraceFrom(source)
        }
    }

    private data class OptBacktraceFrom(val sourceError: Inner, val backtrace: Backtrace?) : Error {
        override fun source(): Error = sourceError

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

    private data class ArcBacktraceFrom(val sourceError: Inner, val backtrace: Backtrace) : Error {
        override fun source(): Error = sourceError

        override fun provide(request: Request) {
            super.provide(request)
            request.provideBacktrace(backtrace)
        }

        companion object {
            fun from(source: Inner): ArcBacktraceFrom = ArcBacktraceFrom(source, Backtrace("captured"))
        }
    }

    private data class AnyhowBacktrace(val sourceError: Error, val backtrace: Backtrace) : Error {
        override fun source(): Error = sourceError

        override fun provide(request: Request) {
            super.provide(request)
            request.provideBacktrace(backtrace)
        }
    }

    private data class BoxDynErrorBacktrace(val sourceError: Error) : Error {
        override fun source(): Error = sourceError

        override fun provide(request: Request) {
            sourceError.provide(request)
            super.provide(request)
        }
    }

    private sealed class EnumPlainBacktrace : Error {
        data class Test(val backtrace: Backtrace) : EnumPlainBacktrace() {
            override fun provide(request: Request) {
                request.provideBacktrace(backtrace)
            }
        }
    }

    private sealed class EnumExplicitBacktrace : Error {
        data class Test(val backtrace: Backtrace) : EnumExplicitBacktrace() {
            override fun provide(request: Request) {
                request.provideBacktrace(backtrace)
            }
        }
    }

    private sealed class EnumOptBacktrace : Error {
        data class Test(val backtrace: Backtrace?) : EnumOptBacktrace() {
            override fun provide(request: Request) {
                if (backtrace != null) {
                    request.provideBacktrace(backtrace)
                }
            }
        }
    }

    private sealed class EnumArcBacktrace : Error {
        data class Test(val backtrace: Backtrace) : EnumArcBacktrace() {
            override fun provide(request: Request) {
                request.provideBacktrace(backtrace)
            }
        }
    }

    private sealed class EnumBacktraceFrom : Error {
        data class Test(val sourceError: Inner, val backtrace: Backtrace) : EnumBacktraceFrom() {
            override fun source(): Error = sourceError

            override fun provide(request: Request) {
                super.provide(request)
                request.provideBacktrace(backtrace)
            }
        }

        companion object {
            fun from(source: Inner): EnumBacktraceFrom = Test(source, Backtrace("captured"))
        }
    }

    private sealed class EnumCombinedBacktraceFrom : Error {
        data class Test(val sourceError: InnerBacktrace) : EnumCombinedBacktraceFrom() {
            override fun source(): Error = sourceError

            override fun provide(request: Request) {
                sourceError.provide(request)
                super.provide(request)
            }
        }

        companion object {
            fun from(source: InnerBacktrace): EnumCombinedBacktraceFrom = Test(source)
        }
    }

    private sealed class EnumOptBacktraceFrom : Error {
        data class Test(val sourceError: Inner, val backtrace: Backtrace?) : EnumOptBacktraceFrom() {
            override fun source(): Error = sourceError

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

    private sealed class EnumArcBacktraceFrom : Error {
        data class Test(val sourceError: Inner, val backtrace: Backtrace) : EnumArcBacktraceFrom() {
            override fun source(): Error = sourceError

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

    private fun requestBacktrace(error: Error): Backtrace? {
        val request = Request()
        error.provide(request)
        return request.backtrace()
    }
}
