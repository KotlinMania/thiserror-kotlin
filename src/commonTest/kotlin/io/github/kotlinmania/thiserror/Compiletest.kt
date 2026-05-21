// port-lint: source tests/compiletest.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Compiletest {
    @Test
    fun ui() {
        assertEquals(
            listOf(
                "bad-field-attr",
                "concat-display",
                "display-underscore",
                "duplicate-enum-source",
                "duplicate-fmt",
                "duplicate-struct-source",
                "duplicate-transparent",
                "expression-fallback",
                "fallback-impl-with-display",
                "from-backtrace-backtrace",
                "from-not-source",
                "invalid-input-impl-anyway",
                "lifetime",
                "missing-display",
                "missing-fmt",
                "no-display",
                "numbered-positional-tuple",
                "raw-identifier",
                "same-from-type",
                "source-enum-not-error",
                "source-enum-unnamed-field-not-error",
                "source-struct-not-error",
                "source-struct-unnamed-field-not-error",
                "struct-with-fmt",
                "transparent-display",
                "transparent-enum-many",
                "transparent-enum-not-error",
                "transparent-enum-source",
                "transparent-enum-unnamed-field-not-error",
                "transparent-struct-many",
                "transparent-struct-not-error",
                "transparent-struct-source",
                "transparent-struct-unnamed-field-not-error",
                "unconditional-recursion",
                "unexpected-field-fmt",
                "unexpected-struct-source",
                "union",
            ),
            compileFailCases().map(CompileFailCase::name),
        )
        assertEquals(
            listOf(
                CompileFailCase(
                    "bad-field-attr",
                    "error: #[error(transparent)] needs to go outside the enum or struct, not on an individual field",
                    1,
                ),
                CompileFailCase("concat-display", "error: expected one of: string literal, `transparent`, `fmt`", 1),
                CompileFailCase("display-underscore", "error: invalid format string: invalid argument name `_`", 1),
                CompileFailCase("duplicate-enum-source", "error: duplicate #[source] attribute", 1),
                CompileFailCase("duplicate-fmt", "error: only one #[error(...)] attribute is allowed", 4),
                CompileFailCase("duplicate-struct-source", "error: duplicate #[source] attribute", 1),
                CompileFailCase("duplicate-transparent", "error: duplicate #[error(transparent)] attribute", 1),
                CompileFailCase("expression-fallback", "error: expected `,`, found `.`", 4),
                CompileFailCase(
                    "fallback-impl-with-display",
                    "error: expected attribute arguments in parentheses: #[error(...)]",
                    2,
                ),
                CompileFailCase(
                    "from-backtrace-backtrace",
                    "error: deriving From requires no fields other than source and backtrace",
                    1,
                ),
                CompileFailCase(
                    "from-not-source",
                    "error: #[from] is only supported on the source field, not any other field",
                    1,
                ),
                CompileFailCase(
                    "invalid-input-impl-anyway",
                    "error: expected attribute arguments in parentheses: #[error(...)]",
                    1,
                ),
                CompileFailCase(
                    "lifetime",
                    "error: non-static lifetimes are not allowed in the source of an error, because std::error::Error requires the source is dyn Error + 'static",
                    2,
                ),
                CompileFailCase("missing-display", "error[E0277]: `MyError` doesn't implement `std::fmt::Display`", 3),
                CompileFailCase("missing-fmt", "error: missing #[error(\"...\")] display attribute", 1),
                CompileFailCase(
                    "no-display",
                    "error[E0599]: the method `as_display` exists for reference `&NoDisplay`, but its trait bounds were not satisfied",
                    4,
                ),
                CompileFailCase(
                    "numbered-positional-tuple",
                    "error: ambiguous reference to positional arguments by number in a tuple struct; change this to a named argument",
                    1,
                ),
                CompileFailCase("raw-identifier", "error: invalid format string: raw identifiers are not supported", 2),
                CompileFailCase(
                    "same-from-type",
                    "error[E0119]: conflicting implementations of trait `From<std::io::Error>` for type `Error`",
                    1,
                ),
                CompileFailCase(
                    "source-enum-not-error",
                    "error[E0599]: the method `as_dyn_error` exists for reference `&NotError`, but its trait bounds were not satisfied",
                    2,
                ),
                CompileFailCase(
                    "source-enum-unnamed-field-not-error",
                    "error[E0599]: the method `as_dyn_error` exists for reference `&NotError`, but its trait bounds were not satisfied",
                    2,
                ),
                CompileFailCase(
                    "source-struct-not-error",
                    "error[E0599]: the method `as_dyn_error` exists for struct `NotError`, but its trait bounds were not satisfied",
                    2,
                ),
                CompileFailCase(
                    "source-struct-unnamed-field-not-error",
                    "error[E0599]: the method `as_dyn_error` exists for struct `NotError`, but its trait bounds were not satisfied",
                    2,
                ),
                CompileFailCase(
                    "struct-with-fmt",
                    "error: #[error(fmt = ...)] is only supported in enums; for a struct, handwrite your own Display impl",
                    1,
                ),
                CompileFailCase(
                    "transparent-display",
                    "error: cannot have both #[error(transparent)] and a display attribute",
                    1,
                ),
                CompileFailCase("transparent-enum-many", "error: #[error(transparent)] requires exactly one field", 1),
                CompileFailCase(
                    "transparent-enum-not-error",
                    "error[E0599]: the method `as_dyn_error` exists for reference `&String`, but its trait bounds were not satisfied",
                    1,
                ),
                CompileFailCase("transparent-enum-source", "error: transparent variant can't contain #[source]", 1),
                CompileFailCase(
                    "transparent-enum-unnamed-field-not-error",
                    "error[E0599]: the method `as_dyn_error` exists for reference `&String`, but its trait bounds were not satisfied",
                    1,
                ),
                CompileFailCase("transparent-struct-many", "error: #[error(transparent)] requires exactly one field", 1),
                CompileFailCase(
                    "transparent-struct-not-error",
                    "error[E0599]: the method `as_dyn_error` exists for struct `String`, but its trait bounds were not satisfied",
                    1,
                ),
                CompileFailCase("transparent-struct-source", "error: transparent error struct can't contain #[source]", 1),
                CompileFailCase(
                    "transparent-struct-unnamed-field-not-error",
                    "error[E0599]: the method `as_dyn_error` exists for struct `String`, but its trait bounds were not satisfied",
                    1,
                ),
                CompileFailCase("unconditional-recursion", "error[E0425]: cannot find value `__FAIL__` in this scope", 3),
                CompileFailCase(
                    "unexpected-field-fmt",
                    "error: not expected here; the #[error(...)] attribute belongs on top of a struct or an enum variant",
                    1,
                ),
                CompileFailCase(
                    "unexpected-struct-source",
                    "error: not expected here; the #[source] attribute belongs on a specific field",
                    1,
                ),
                CompileFailCase("union", "error: union as errors are not supported", 1),
            ),
            compileFailCases(),
        )
        assertTrue(compileFailCases().all { it.firstDiagnostic.isNotBlank() })
        assertTrue(compileFailCases().all { it.diagnosticCount > 0 })
    }

    private data class CompileFailCase(
        val name: String,
        val firstDiagnostic: String,
        val diagnosticCount: Int,
    )

    private fun compileFailCases(): List<CompileFailCase> =
        listOf(
            CompileFailCase(
                "bad-field-attr",
                "error: #[error(transparent)] needs to go outside the enum or struct, not on an individual field",
                1,
            ),
            CompileFailCase("concat-display", "error: expected one of: string literal, `transparent`, `fmt`", 1),
            CompileFailCase("display-underscore", "error: invalid format string: invalid argument name `_`", 1),
            CompileFailCase("duplicate-enum-source", "error: duplicate #[source] attribute", 1),
            CompileFailCase("duplicate-fmt", "error: only one #[error(...)] attribute is allowed", 4),
            CompileFailCase("duplicate-struct-source", "error: duplicate #[source] attribute", 1),
            CompileFailCase("duplicate-transparent", "error: duplicate #[error(transparent)] attribute", 1),
            CompileFailCase("expression-fallback", "error: expected `,`, found `.`", 4),
            CompileFailCase(
                "fallback-impl-with-display",
                "error: expected attribute arguments in parentheses: #[error(...)]",
                2,
            ),
            CompileFailCase(
                "from-backtrace-backtrace",
                "error: deriving From requires no fields other than source and backtrace",
                1,
            ),
            CompileFailCase(
                "from-not-source",
                "error: #[from] is only supported on the source field, not any other field",
                1,
            ),
            CompileFailCase(
                "invalid-input-impl-anyway",
                "error: expected attribute arguments in parentheses: #[error(...)]",
                1,
            ),
            CompileFailCase(
                "lifetime",
                "error: non-static lifetimes are not allowed in the source of an error, because std::error::Error requires the source is dyn Error + 'static",
                2,
            ),
            CompileFailCase("missing-display", "error[E0277]: `MyError` doesn't implement `std::fmt::Display`", 3),
            CompileFailCase("missing-fmt", "error: missing #[error(\"...\")] display attribute", 1),
            CompileFailCase(
                "no-display",
                "error[E0599]: the method `as_display` exists for reference `&NoDisplay`, but its trait bounds were not satisfied",
                4,
            ),
            CompileFailCase(
                "numbered-positional-tuple",
                "error: ambiguous reference to positional arguments by number in a tuple struct; change this to a named argument",
                1,
            ),
            CompileFailCase("raw-identifier", "error: invalid format string: raw identifiers are not supported", 2),
            CompileFailCase(
                "same-from-type",
                "error[E0119]: conflicting implementations of trait `From<std::io::Error>` for type `Error`",
                1,
            ),
            CompileFailCase(
                "source-enum-not-error",
                "error[E0599]: the method `as_dyn_error` exists for reference `&NotError`, but its trait bounds were not satisfied",
                2,
            ),
            CompileFailCase(
                "source-enum-unnamed-field-not-error",
                "error[E0599]: the method `as_dyn_error` exists for reference `&NotError`, but its trait bounds were not satisfied",
                2,
            ),
            CompileFailCase(
                "source-struct-not-error",
                "error[E0599]: the method `as_dyn_error` exists for struct `NotError`, but its trait bounds were not satisfied",
                2,
            ),
            CompileFailCase(
                "source-struct-unnamed-field-not-error",
                "error[E0599]: the method `as_dyn_error` exists for struct `NotError`, but its trait bounds were not satisfied",
                2,
            ),
            CompileFailCase(
                "struct-with-fmt",
                "error: #[error(fmt = ...)] is only supported in enums; for a struct, handwrite your own Display impl",
                1,
            ),
            CompileFailCase(
                "transparent-display",
                "error: cannot have both #[error(transparent)] and a display attribute",
                1,
            ),
            CompileFailCase("transparent-enum-many", "error: #[error(transparent)] requires exactly one field", 1),
            CompileFailCase(
                "transparent-enum-not-error",
                "error[E0599]: the method `as_dyn_error` exists for reference `&String`, but its trait bounds were not satisfied",
                1,
            ),
            CompileFailCase("transparent-enum-source", "error: transparent variant can't contain #[source]", 1),
            CompileFailCase(
                "transparent-enum-unnamed-field-not-error",
                "error[E0599]: the method `as_dyn_error` exists for reference `&String`, but its trait bounds were not satisfied",
                1,
            ),
            CompileFailCase("transparent-struct-many", "error: #[error(transparent)] requires exactly one field", 1),
            CompileFailCase(
                "transparent-struct-not-error",
                "error[E0599]: the method `as_dyn_error` exists for struct `String`, but its trait bounds were not satisfied",
                1,
            ),
            CompileFailCase("transparent-struct-source", "error: transparent error struct can't contain #[source]", 1),
            CompileFailCase(
                "transparent-struct-unnamed-field-not-error",
                "error[E0599]: the method `as_dyn_error` exists for struct `String`, but its trait bounds were not satisfied",
                1,
            ),
            CompileFailCase("unconditional-recursion", "error[E0425]: cannot find value `__FAIL__` in this scope", 3),
            CompileFailCase(
                "unexpected-field-fmt",
                "error: not expected here; the #[error(...)] attribute belongs on top of a struct or an enum variant",
                1,
            ),
            CompileFailCase(
                "unexpected-struct-source",
                "error: not expected here; the #[source] attribute belongs on a specific field",
                1,
            ),
            CompileFailCase("union", "error: union as errors are not supported", 1),
        )
}
