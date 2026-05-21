// port-lint: source tests/test_display.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals

class TestDisplay {
    private fun assertDisplay(expected: String, value: Any?) {
        assertEquals(expected, value.toString())
    }

    @Test
    fun testBraced() {
        data class BracedError(val msg: String) : StdError {
            override fun toString(): String = "braced error: $msg"
        }

        assertDisplay("braced error: T", BracedError("T"))
    }

    @Test
    fun testBracedUnused() {
        data class BracedUnusedError(val extra: Int) : StdError {
            override fun toString(): String = "braced error"
        }

        assertDisplay("braced error", BracedUnusedError(0))
    }

    @Test
    fun testTuple() {
        data class TupleError(val value: Int) : StdError {
            override fun toString(): String = "tuple error: $value"
        }

        assertDisplay("tuple error: 0", TupleError(0))
    }

    @Test
    fun testUnit() {
        class UnitError : StdError {
            override fun toString(): String = "unit error"
        }

        assertDisplay("unit error", UnitError())
    }

    @Test
    fun testEnum() {
        data class EnumBraced(val id: Int) : StdError {
            override fun toString(): String = "braced error: $id"
        }
        data class EnumTuple(val value: Int) : StdError {
            override fun toString(): String = "tuple error: $value"
        }
        class EnumUnit : StdError {
            override fun toString(): String = "unit error"
        }

        assertDisplay("braced error: 0", EnumBraced(0))
        assertDisplay("tuple error: 0", EnumTuple(0))
        assertDisplay("unit error", EnumUnit())
    }

    @Test
    fun testConstants() {
        data class ConstantsError(val id: String) : StdError {
            override fun toString(): String = "$MSG: \"$id\" (code $CODE)"
        }

        assertDisplay("failed to do: \"\" (code 9)", ConstantsError(""))
    }

    @Test
    fun testInherit() {
        data class InheritSome(val message: String) : StdError {
            override fun toString(): String = message
        }
        data class InheritOther(val ignored: String) : StdError {
            override fun toString(): String = "other error"
        }

        assertDisplay("some error", InheritSome("some error"))
        assertDisplay("other error", InheritOther("..."))
    }

    @Test
    fun testBraceEscape() {
        class BraceEscapeError : StdError {
            override fun toString(): String = "fn main() {}"
        }

        assertDisplay("fn main() {}", BraceEscapeError())
    }

    @Test
    fun testExpr() {
        class ExprError : StdError {
            override fun toString(): String = "1 + 1 = ${1 + 1}"
        }

        assertDisplay("1 + 1 = 2", ExprError())
    }

    @Test
    fun testNested() {
        data class NestedError(val value: Boolean) : StdError {
            override fun toString(): String = "!bool = ${!value}"
        }

        assertDisplay("!bool = false", NestedError(true))
    }

    @Test
    fun testMatch() {
        data class MatchError(val message: String, val number: Int?) : StdError {
            override fun toString(): String {
                val intro = number?.let { "error occurred with $it" } ?: "there was an empty error"
                return "$intro: $message"
            }
        }

        assertDisplay("error occurred with 1: ...", MatchError("...", 1))
        assertDisplay("there was an empty error: ...", MatchError("...", null))
    }

    @Test
    fun testNestedDisplay() {
        data class NestedDisplayError(val message: String, val number: Int?) : StdError {
            override fun toString(): String {
                val intro = number?.let { "error occurred with $it" } ?: "there was an empty error"
                return "$intro: $message"
            }
        }

        assertDisplay("error occurred with 1: ...", NestedDisplayError("...", 1))
        assertDisplay("there was an empty error: ...", NestedDisplayError("...", null))
    }

    @Test
    fun testVoid() {
        val error: VoidError? = null

        assertEquals(null, error)
    }

    @Test
    fun testMixed() {
        data class MixedError(val a: Int, val d: Int) : StdError {
            override fun toString(): String = "a=$a :: b=1 :: c=2 :: d=3"
        }

        assertDisplay("a=0 :: b=1 :: c=2 :: d=3", MixedError(0, 0))
    }

    @Test
    fun testInts() {
        data class IntTupleError(val first: Int, val second: Int) : StdError {
            override fun toString(): String = "error $first"
        }
        data class IntStructError(val value: Int) : StdError {
            override fun toString(): String = "error ?"
        }

        assertDisplay("error 9", IntTupleError(9, 0))
        assertDisplay("error ?", IntStructError(0))
    }

    @Test
    fun testTrailingComma() {
        data class TrailingCommaError(val value: Char) : StdError {
            override fun toString(): String = "error $value"
        }

        assertDisplay("error ?", TrailingCommaError('?'))
    }

    @Test
    fun testField() {
        data class Inner(val data: Int)
        data class FieldError(val inner: Inner) : StdError {
            override fun toString(): String = inner.data.toString()
        }

        assertDisplay("0", FieldError(Inner(0)))
    }

    @Test
    fun testNestedTupleField() {
        data class Inner(val value: Int)
        data class NestedTupleFieldError(val inner: Inner) : StdError {
            override fun toString(): String = inner.value.toString()
        }

        assertDisplay("0", NestedTupleFieldError(Inner(0)))
    }

    @Test
    fun testPointer() {
        data class PointerBox(val value: Int) {
            fun pointerString(): String = "ptr($value)"
        }
        data class PointerError(val field: PointerBox) : StdError {
            override fun toString(): String = field.pointerString()
        }

        val field = PointerBox(-1)
        assertEquals(field.pointerString(), PointerError(field).toString())
    }

    @Test
    fun testMacroRulesVariantFromCallSite() {
        data class Error0Repro(val value: UByte) : StdError {
            override fun toString(): String = value.toString()
        }
        data class Error1Repro(val value: UByte) : StdError {
            override fun toString(): String = value.toString()
        }

        assertDisplay("0", Error0Repro(0u))
        assertDisplay("0", Error1Repro(0u))
    }

    @Test
    fun testMacroRulesMessageFromCallSite() {
        data class MacroUnnamedError(val value: UByte) : StdError {
            override fun toString(): String = value.toString()
        }
        data class MacroNamedError(val x: UByte) : StdError {
            override fun toString(): String = x.toString()
        }

        assertDisplay("0", MacroUnnamedError(0u))
        assertDisplay("0", MacroNamedError(0u))
    }

    @Test
    fun testRaw() {
        data class RawError(val functionName: String) : StdError {
            override fun toString(): String = "braced raw error: $functionName"
        }

        assertDisplay("braced raw error: T", RawError("T"))
    }

    @Test
    fun testRawEnum() {
        data class RawEnumBracedError(val functionName: String) : StdError {
            override fun toString(): String = "braced raw error: $functionName"
        }

        assertDisplay("braced raw error: T", RawEnumBracedError("T"))
    }

    @Test
    fun testKeyword() {
        class KeywordError : StdError {
            override fun toString(): String = "error: 1"
        }

        assertDisplay("error: 1", KeywordError())
    }

    @Test
    fun testSelf() {
        class SelfError : StdError {
            override fun toString(): String = "error: Error"
        }

        assertDisplay("error: Error", SelfError())
    }

    @Test
    fun testStrSpecialChars() {
        data class SpecialCharsError(private val message: String) : StdError {
            override fun toString(): String = message
        }

        assertDisplay("brace left {", SpecialCharsError("brace left {"))
        assertDisplay("brace left 2 {", SpecialCharsError("brace left 2 {"))
        assertDisplay("brace left 3 {", SpecialCharsError("brace left 3 {"))
        assertDisplay("brace right }", SpecialCharsError("brace right }"))
        assertDisplay("brace right 2 }", SpecialCharsError("brace right 2 }"))
        assertDisplay("brace right 3 }", SpecialCharsError("brace right 3 }"))
        assertDisplay("new_line", SpecialCharsError("new_line"))
        assertDisplay("escape24 x", SpecialCharsError("escape24 x"))
    }

    @Test
    fun testRawStr() {
        data class RawStringError(private val message: String) : StdError {
            override fun toString(): String = message
        }

        assertDisplay("raw brace left {", RawStringError("raw brace left {"))
        assertDisplay("raw brace left 2 \\x7B", RawStringError("raw brace left 2 \\x7B"))
        assertDisplay("raw brace right }", RawStringError("raw brace right }"))
        assertDisplay("raw brace right 2 \\x7D", RawStringError("raw brace right 2 \\x7D"))
    }

    @Test
    fun testFmtPath() {
        class FmtPathUnit : StdError {
            override fun toString(): String = unit()
        }
        data class FmtPathTuple(val key: Int, val value: Int) : StdError {
            override fun toString(): String = pair(key, value)
        }
        data class FmtPathEntry(val key: Int, val value: Int) : StdError {
            override fun toString(): String = pair(key, value)
        }
        data class FmtPathI16(val value: Int) : StdError {
            override fun toString(): String = octal(value)
        }
        data class FmtPathI32(val value: Int) : StdError {
            override fun toString(): String = octal(value)
        }
        data class FmtPathI64(val value: Int) : StdError {
            override fun toString(): String = value.toString(8)
        }
        data class FmtPathOther(val value: Boolean) : StdError {
            override fun toString(): String = "...$value"
        }

        assertDisplay("unit=", FmtPathUnit())
        assertDisplay("pair=10:0", FmtPathTuple(10, 0))
        assertDisplay("pair=10:0", FmtPathEntry(10, 0))
        assertDisplay("0o777", FmtPathI16(511))
        assertDisplay("0o777", FmtPathI32(511))
        assertDisplay("777", FmtPathI64(511))
        assertDisplay("...false", FmtPathOther(false))
    }

    @Test
    fun testFmtPathInherited() {
        data class FmtPathInheritedI16(val value: Int) : StdError {
            override fun toString(): String = octal(value)
        }
        data class FmtPathInheritedI32(val value: Int) : StdError {
            override fun toString(): String = octal(value)
        }
        data class FmtPathInheritedI64(val value: Int) : StdError {
            override fun toString(): String = value.toString(8)
        }
        data class FmtPathInheritedOther(val value: Boolean) : StdError {
            override fun toString(): String = "...$value"
        }

        assertDisplay("0o777", FmtPathInheritedI16(511))
        assertDisplay("0o777", FmtPathInheritedI32(511))
        assertDisplay("777", FmtPathInheritedI64(511))
        assertDisplay("...false", FmtPathInheritedOther(false))
    }

    private sealed interface VoidError : StdError

    private companion object {
        const val MSG = "failed to do"
        const val CODE = 9

        fun unit(): String = "unit="

        fun pair(key: Int, value: Int): String = "pair=$key:$value"

        fun octal(value: Int): String = "0o${value.toString(8)}"
    }
}
