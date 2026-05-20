// port-lint: source tests/test_source.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TestSource {
    private data class IoError(val message: String) : Error {
        override fun toString(): String = message
    }

    private data class ImplicitSource(val inner: Error) : Error {
        override fun source(): Error = inner

        override fun toString(): String = "implicit source"
    }

    private data class ExplicitSource(val sourceText: String, val io: Error) : Error {
        override fun source(): Error = io

        override fun toString(): String = "explicit source"
    }

    private data class BoxedSource(val inner: Error) : Error {
        override fun source(): Error = inner

        override fun toString(): String = "boxed source"
    }

    private sealed class MacroSource : Error {
        data class Variant(val inner: Error) : MacroSource() {
            override fun source(): Error = inner

            override fun toString(): String = "Something"
        }
    }

    @Test
    fun testImplicitSource() {
        val io = IoError("oh no!")
        val error = ImplicitSource(io)

        assertSame(io, error.source())
    }

    @Test
    fun testExplicitSource() {
        val io = IoError("oh no!")
        val error = ExplicitSource("", io)

        assertSame(io, error.source())
    }

    @Test
    fun testBoxedSource() {
        val source = IoError("oh no!")
        val error = BoxedSource(source)

        assertSame(source, error.source())
    }

    @Test
    fun testNotSource() {
        data class NotSource(val source: Char, val destination: Char) : Error {
            override fun toString(): String = "$source ==> $destination"
        }

        val error = NotSource('S', 'D')

        assertEquals("S ==> D", error.toString())
        assertEquals(null, error.source())
    }
}
