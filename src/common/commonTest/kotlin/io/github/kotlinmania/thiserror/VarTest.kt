package io.github.kotlinmania.thiserror

import io.github.kotlinmania.thiserror.compat.fmt.Alignment
import io.github.kotlinmania.thiserror.compat.fmt.DebugAsHex
import io.github.kotlinmania.thiserror.compat.fmt.FmtResult
import io.github.kotlinmania.thiserror.compat.fmt.Formatter
import io.github.kotlinmania.thiserror.compat.fmt.FormattingOptions
import io.github.kotlinmania.thiserror.compat.fmt.Pointer
import io.github.kotlinmania.thiserror.compat.fmt.Sign
import io.github.kotlinmania.thiserror.compat.fmt.Write
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class VarTest {
    @Test
    fun delegatesToTheValueWithTheSameFormatter() {
        val options =
            FormattingOptions(
                sign = Sign.Plus,
                signAwareZeroPad = true,
                alternate = true,
                fill = 0x1f642,
                align = Alignment.Center,
                width = 18,
                precision = 3,
                debugAsHex = DebugAsHex.Upper,
            )
        val writes = mutableListOf<String>()
        val formatter =
            Formatter({
                writes.add(it)
                FmtResult.Ok
            }, options)
        var calls = 0
        val value =
            Pointer {
                calls++
                assertSame(formatter, it)
                assertEquals(options, it.options())
                assertEquals(0x1f642, it.fill())
                assertEquals(Alignment.Center, it.align())
                assertEquals(18, it.width())
                assertEquals(3, it.precision())
                assertEquals(true, it.signPlus())
                assertEquals(false, it.signMinus())
                assertEquals(true, it.alternate())
                assertEquals(true, it.signAwareZeroPad())
                it.writeStr("formatted value")
            }
        val variable = Var(value)
        val pointer: Pointer = variable
        assertSame(value, variable.value)
        assertEquals(FmtResult.Ok, pointer.fmt(formatter))
        assertEquals(1, calls)
        assertEquals(listOf("formatted value"), writes)
    }

    @Test
    fun returnsWriterFailureWithoutRetrying() {
        var calls = 0
        val formatter =
            Formatter({
                calls++
                FmtResult.Err
            })
        val variable = Var(Pointer { it.writeStr("value") })
        assertEquals(FmtResult.Err, variable.fmt(formatter))
        assertEquals(1, calls)
    }

    @Test
    fun propagatesTheOriginalExceptionAfterPreviousWrites() {
        val failure = IllegalStateException("writer failed")
        val writes = mutableListOf<String>()
        val formatter =
            Formatter({
                writes.add(it)
                if (writes.size == 2) throw failure
                FmtResult.Ok
            })
        val variable =
            Var(
                Pointer {
                    if (it.writeStr("first") == FmtResult.Err) FmtResult.Err else it.writeStr("second")
                },
            )
        assertSame(failure, assertFailsWith<IllegalStateException> { variable.fmt(formatter) })
        assertEquals(listOf("first", "second"), writes)
    }

    @Test
    fun preservesWriterCharacterDispatchAndOptionIsolation() {
        val chars = mutableListOf<Int>()
        val writer =
            object : Write {
                override fun writeStr(value: String): FmtResult = error("unexpected string write: $value")

                override fun writeChar(value: Int): FmtResult {
                    chars.add(value)
                    return FmtResult.Err
                }
            }
        val formatter = Formatter(writer)
        val changed = formatter.withOptions(FormattingOptions(width = 9, sign = Sign.Minus))
        assertEquals(null, formatter.width())
        assertEquals(9, changed.width())
        assertEquals(true, changed.signMinus())
        assertEquals(FmtResult.Err, Var(Pointer { it.writeChar(0x1f642) }).fmt(changed))
        assertEquals(listOf(0x1f642), chars)
    }

    @Test
    fun writesOneCompleteUnicodeScalarAndChecksOptionDomains() {
        val writes = mutableListOf<String>()
        val writer =
            Write {
                writes.add(it)
                FmtResult.Ok
            }
        assertEquals(FmtResult.Ok, writer.writeChar(0x1f642))
        assertEquals(listOf("🙂"), writes)
        assertFailsWith<IllegalArgumentException> { writer.writeChar(0xd800) }
        assertFailsWith<IllegalArgumentException> { FormattingOptions(fill = 0x110000) }
        assertFailsWith<IllegalArgumentException> { FormattingOptions(width = -1) }
        assertFailsWith<IllegalArgumentException> { FormattingOptions(precision = 65536) }
        assertEquals(65535, FormattingOptions(width = 65535).width)
    }
}
