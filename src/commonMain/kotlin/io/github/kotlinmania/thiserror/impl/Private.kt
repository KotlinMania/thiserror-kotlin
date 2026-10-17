package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.Ident
import io.github.kotlinmania.procmacro2.Span
import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.procmacro2.TokenTree
import io.github.kotlinmania.quote.ToTokens

/**
 * Token generator emitting the `__private` identifier for macro-expanded code.
 */
public object Private : ToTokens {
    override fun toTokens(tokens: TokenStream) {
        tokens.extendTokenTrees(listOf(TokenTree.Ident(Ident.new("__private", Span.callSite()))))
    }
}
