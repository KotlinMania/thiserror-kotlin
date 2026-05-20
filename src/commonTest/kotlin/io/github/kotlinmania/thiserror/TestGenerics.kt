// port-lint: source tests/test_generics.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TestGenerics {
    private class NoFormat

    private class DebugOnly {
        override fun toString(): String = "DebugOnly"
    }

    private class DisplayOnly {
        override fun toString(): String = "display only"
    }

    private class DebugAndDisplay {
        override fun toString(): String = "debug and display"
    }

    private sealed class EnumDebugGeneric<E> : Error {
        data class FatalError<E>(val value: E) : EnumDebugGeneric<E>() {
            override fun toString(): String = value.toString()
        }
    }

    private sealed class EnumFromGeneric<E> : Error {
        data class Source<E>(val sourceError: EnumDebugGeneric<E>) : EnumFromGeneric<E>() {
            override fun source(): Error = sourceError

            override fun toString(): String = "enum from generic"
        }
    }

    private sealed class EnumCompound<HasDisplay, HasDebug, HasNeither> : Error {
        data class DisplayDebug<HasDisplay, HasDebug, HasNeither>(
            val display: HasDisplay,
            val debug: HasDebug,
        ) : EnumCompound<HasDisplay, HasDebug, HasNeither>() {
            override fun toString(): String = "$display $debug"
        }

        data class Display<HasDisplay, HasDebug, HasNeither>(
            val display: HasDisplay,
            val neither: HasNeither,
        ) : EnumCompound<HasDisplay, HasDebug, HasNeither>() {
            override fun toString(): String = display.toString()
        }

        data class Debug<HasDisplay, HasDebug, HasNeither>(
            val neither: HasNeither,
            val debug: HasDebug,
        ) : EnumCompound<HasDisplay, HasDebug, HasNeither>() {
            override fun toString(): String = debug.toString()
        }
    }

    private sealed class EnumTransparentGeneric<E : Error> : Error {
        data class Other<E : Error>(val inner: E) : EnumTransparentGeneric<E>() {
            override fun source(): Error? = inner.source()

            override fun toString(): String = inner.toString()
        }
    }

    private data class StructDebugGeneric<E>(val underlying: E) : Error {
        override fun toString(): String = underlying.toString()
    }

    private data class StructFromGeneric<E>(val sourceError: StructDebugGeneric<E>) : Error {
        override fun source(): Error = sourceError
    }

    private data class StructTransparentGeneric<E : Error>(val inner: E) : Error {
        override fun source(): Error? = inner.source()

        override fun toString(): String = inner.toString()
    }

    private sealed class AssociatedTypeError<T> : Error {
        class Other<T> : AssociatedTypeError<T>() {
            override fun toString(): String = "couldn't parse matrix"
        }

        data class EntryParseError<T>(val error: String) : AssociatedTypeError<T>() {
            override fun toString(): String = "couldn't parse entry: $error"
        }
    }

    @Test
    fun testDisplayEnumCompound() {
        var instance: EnumCompound<DisplayOnly, DebugOnly, NoFormat>

        instance = EnumCompound.DisplayDebug(DisplayOnly(), DebugOnly())
        assertEquals("display only DebugOnly", instance.toString())

        instance = EnumCompound.Display(DisplayOnly(), NoFormat())
        assertEquals("display only", instance.toString())

        instance = EnumCompound.Debug(NoFormat(), DebugOnly())
        assertEquals("DebugOnly", instance.toString())
    }

    @Test
    fun testNoBoundOnNamedFmt() {
        data class NamedFmtError<T>(val thing: T) : Error {
            override fun toString(): String = "..."
        }

        val error = NamedFmtError(DebugOnly())
        assertEquals("...", error.toString())
    }

    @Test
    fun testMultipleBound() {
        data class MultipleBoundError(val thing: IntLike) : Error {
            override fun toString(): String = "0x${thing.hexLower()} 0x${thing.hexUpper()}"
        }

        val error = MultipleBoundError(IntLike(0xFF))
        assertEquals("0xff 0xFF", error.toString())
    }

    @Test
    fun genericDefinitionsKeepTheirGeneratedBounds() {
        val source = EnumDebugGeneric.FatalError(DebugAndDisplay())
        val from = EnumFromGeneric.Source(source)
        val transparent = EnumTransparentGeneric.Other(source)
        val struct = StructDebugGeneric(DebugOnly())
        val structFrom = StructFromGeneric(struct)
        val structTransparent = StructTransparentGeneric(source)

        assertSame(source, from.source())
        assertEquals("debug and display", transparent.toString())
        assertEquals("DebugOnly", struct.toString())
        assertSame(struct, structFrom.source())
        assertEquals("debug and display", structTransparent.toString())
        assertEquals("couldn't parse matrix", AssociatedTypeError.Other<String>().toString())
        assertEquals("couldn't parse entry: bad", AssociatedTypeError.EntryParseError<String>("bad").toString())
    }

    private data class IntLike(val value: Int) {
        fun hexLower(): String = value.toString(16)

        fun hexUpper(): String = value.toString(16).uppercase()
    }
}
