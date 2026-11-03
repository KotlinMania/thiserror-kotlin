package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.syn.DeriveInput
import io.github.kotlinmania.syn.DeriveInputParse
import io.github.kotlinmania.syn.SynResult
import io.github.kotlinmania.syn.parse2

/**
 * Expand a `#[derive(Error)]` invocation into its generated token stream.
 */
public fun deriveError(input: TokenStream): TokenStream {
    val parseResult: SynResult<DeriveInput> = parse2(DeriveInputParse::parse, input)
    return when (parseResult) {
        is SynResult.Success -> derive(parseResult.value)
        is SynResult.Failure -> parseResult.error.toCompileError()
    }
}
