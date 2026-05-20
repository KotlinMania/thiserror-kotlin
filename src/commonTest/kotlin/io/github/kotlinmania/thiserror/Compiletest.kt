// port-lint: source tests/compiletest.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals

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
            compileFailFixtures(),
        )
    }

    private fun compileFailFixtures(): List<String> =
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
        )
}
