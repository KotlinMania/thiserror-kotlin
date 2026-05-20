// port-lint: source tests/test_expr.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals

class TestExpr {
    private sealed class CompilerError : StdError {
        data class TooManyShiftBits(
            val isLeft: Boolean,
            val maximum: ULong,
            val current: ULong,
        ) : CompilerError() {
            override fun toString(): String {
                val direction = if (isLeft) "left" else "right"
                return "cannot shift $direction by $maximum or more bits (got $current)"
            }
        }

        data class User(val parts: List<String>) : CompilerError() {
            override fun toString(): String = "#error ${parts.joinToString(" ")}"
        }

        data class IntegerOverflow(val isSigned: Boolean?) : CompilerError() {
            override fun toString(): String = "overflow while parsing ${prefix()}integer literal"

            private fun prefix(): String =
                when (isSigned) {
                    true -> "signed "
                    false -> "unsigned "
                    null -> ""
                }
        }

        data class IntegerOverflow2(val isSigned: Boolean?) : CompilerError() {
            override fun toString(): String =
                "overflow while parsing ${
                    when (isSigned) {
                        true -> "signed "
                        false -> "unsigned "
                        null -> ""
                    }
                }integer literal"
        }
    }

    private data class RustupError(
        val name: String,
        val component: String,
        val suggestion: String?,
    ) : StdError {
        override fun toString(): String {
            val suffix = suggestion?.let { "; did you mean '$it'?" } ?: ""
            return "toolchain '$name' does not contain component $component$suffix"
        }
    }

    private data class AssociatedTypeError(val value: String) : StdError {
        override fun toString(): String = "$value 0"
    }

    @Test
    fun testRcc() {
        assertEquals(
            "cannot shift left by 32 or more bits (got 50)",
            CompilerError.TooManyShiftBits(isLeft = true, maximum = 32u, current = 50u).toString(),
        )
        assertEquals("#error A B C", CompilerError.User(listOf("A", "B", "C")).toString())
        assertEquals(
            "overflow while parsing signed integer literal",
            CompilerError.IntegerOverflow(isSigned = true).toString(),
        )
        assertEquals(
            "overflow while parsing unsigned integer literal",
            CompilerError.IntegerOverflow2(isSigned = false).toString(),
        )
    }

    @Test
    fun testRustup() {
        assertEquals(
            "toolchain 'nightly' does not contain component clipy; did you mean 'clippy'?",
            RustupError("nightly", "clipy", "clippy").toString(),
        )
    }

    @Test
    fun testAssocTypeEqualityConstraint() {
        assertEquals("... 0", AssociatedTypeError("...").toString())
    }
}
