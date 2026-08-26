// port-lint: source impl/src/lib.rs
package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.Ident
import io.github.kotlinmania.procmacro2.Span
import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.procmacro2.TokenTree
import io.github.kotlinmania.quote.ToTokens
import io.github.kotlinmania.syn.DeriveInput
import io.github.kotlinmania.syn.DeriveInputParse
import io.github.kotlinmania.syn.SynResult
import io.github.kotlinmania.syn.parse2

public fun deriveError(input: TokenStream): TokenStream {
    val parseResult: SynResult<DeriveInput> = parse2(DeriveInputParse::parse, input)
    return when (parseResult) {
        is SynResult.Success -> derive(parseResult.value)
        is SynResult.Failure -> parseResult.error.toCompileError()
    }
}

public object Private : ToTokens {
    override fun toTokens(tokens: TokenStream) {
        tokens.extendTokenTrees(listOf(TokenTree.Ident(Ident.new("__private", Span.callSite()))))
    }
}
