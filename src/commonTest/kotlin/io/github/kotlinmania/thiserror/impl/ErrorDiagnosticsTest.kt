package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.procmacro2.TokenTree
import io.github.kotlinmania.syn.DeriveInput
import io.github.kotlinmania.syn.DeriveInputParse
import io.github.kotlinmania.syn.SynResult
import io.github.kotlinmania.syn.parseStr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ErrorDiagnosticsTest {
    @Test
    fun unionDiagnosticReachesFallback() {
        val source = "union Invalid { field: u8 }"
        assertDiagnostic(source, "union as errors are not supported", source)
    }

    @Test
    fun containerAttributeFailureReachesFallback() {
        assertDiagnostic(
            "#[error(transparent)] #[error(transparent)] struct Invalid(u8);",
            "duplicate #[error(transparent)] attribute",
            "#[error(transparent)]",
        )
    }

    @Test
    fun fieldAttributeFailureReachesFallback() {
        assertDiagnostic(
            "struct Invalid { #[source] #[source] source: Problem }",
            "duplicate #[source] attribute",
            "#[source]",
        )
    }

    @Test
    fun variantAttributeFailureReachesFallback() {
        assertDiagnostic(
            "enum Invalid { #[error(transparent)] #[error(transparent)] Variant(Problem) }",
            "duplicate #[error(transparent)] attribute",
            "#[error(transparent)]",
        )
    }

    @Test
    fun tupleShorthandFailureReachesFallback() {
        assertDiagnostic(
            "#[error(\"{0}\", 1)] struct Invalid(u8);",
            "ambiguous reference to positional arguments by number in a tuple struct; change this to a named argument",
            "1",
        )
    }

    @Test
    fun inheritedEnumShorthandFailureReachesFallback() {
        assertDiagnostic(
            "#[error(\"{0}\", 1)] enum Invalid { Variant(u8) }",
            "ambiguous reference to positional arguments by number in a tuple variant; change this to a named argument",
            "1",
        )
    }

    @Test
    fun structValidationFailureReachesFallback() {
        assertDiagnostic(
            "#[error(transparent)] struct Invalid { first: Problem, second: Problem }",
            "#[error(transparent)] requires exactly one field",
            "#[error(transparent)]",
        )
    }

    @Test
    fun enumFieldValidationFailureReachesFallback() {
        assertDiagnostic(
            "enum Invalid { Variant { #[source] first: Problem, #[source] second: Problem } }",
            "duplicate #[source] attribute",
            "#[source]",
        )
    }

    @Test
    fun misplacedFieldDisplayFailureReachesFallback() {
        assertDiagnostic(
            "struct Invalid { #[error(\"bad\")] field: Problem }",
            "not expected here; the #[error(...)] attribute belongs on top of a struct or an enum variant",
            "#[error(\"bad\")]",
        )
    }

    @Test
    fun firstInvalidAttributeKeepsDiagnosticOrder() {
        assertDiagnostic(
            "#[source] #[from] struct Invalid;",
            "not expected here; the #[from] attribute belongs on a specific field",
            "#[from]",
        )
    }

    @Test
    fun successfulStructAndEnumStillExpand() {
        for (source in listOf("#[error(\"problem\")] struct Valid;", "#[error(\"problem\")] enum Valid { First, Second }")) {
            val input = Input.fromSyn(parseInput(source))
            input.validate()
            val expanded = deriveError(TokenStream.fromString(source).getOrThrow()).toString()
            assertFalse(expanded.contains("compile_error"), expanded)
            assertTrue(expanded.contains("Error for Valid"), expanded)
            assertTrue(expanded.contains("Display for Valid"), expanded)
            assertTrue(expanded.contains("problem"), expanded)
        }
    }

    @Test
    fun syntaxFailureEmitsOnlyTheParserDiagnostic() {
        val expanded = deriveError(TokenStream.fromString("struct").getOrThrow()).toString()
        assertTrue(expanded.contains("compile_error"), expanded)
        assertFalse(expanded.contains("automatically_derived"), expanded)
    }

    @Test
    fun existingThrowingHelpersKeepTheirMessages() {
        val construction = assertFailsWith<IllegalArgumentException> {
            Input.fromSyn(parseInput("union Invalid { field: u8 }"))
        }
        assertEquals("union as errors are not supported", construction.message)

        val input = Input.fromSyn(parseInput("#[source] struct Invalid;"))
        val validation = assertFailsWith<IllegalArgumentException> { input.validate() }
        assertEquals("not expected here; the #[source] attribute belongs on a specific field", validation.message)
    }

    private fun assertDiagnostic(source: String, message: String, highlighted: String) {
        val diagnostic =
            when (val parsed = Input.tryFromSyn(parseInput(source))) {
                is SynResult.Success -> assertNotNull(parsed.value.tryValidate().exceptionOrNull())
                is SynResult.Failure -> parsed.error
            }
        assertEquals(message, diagnostic.message)
        assertEquals(highlighted, diagnostic.span().sourceText())
        assertEquals(source.lastIndexOf(highlighted), diagnostic.span().sourceUtf16Range()?.first)

        val expanded = derive(parseInput(source))
        val expectedTokens = diagnostic.toCompileError().toList()
        val actualTokens = expanded.clone().toList().take(expectedTokens.size)
        assertEquals(expectedTokens.map { it.toString() }, actualTokens.map { it.toString() })
        for ((expected, actual) in expectedTokens.zip(actualTokens)) {
            assertEquals(expected.span().sourceText(), actual.span().sourceText())
            if (expected is TokenTree.Group && actual is TokenTree.Group) {
                assertEquals(
                    expected.value.stream().toList().map { it.span().sourceText() },
                    actual.value.stream().toList().map { it.span().sourceText() },
                )
            }
        }
        assertTrue(expanded.toString().contains("Error for Invalid"))
        assertTrue(expanded.toString().contains("Display for Invalid"))
    }

    private fun parseInput(source: String): DeriveInput =
        parseStr(DeriveInputParse::parse, source).getOrThrow()
}
