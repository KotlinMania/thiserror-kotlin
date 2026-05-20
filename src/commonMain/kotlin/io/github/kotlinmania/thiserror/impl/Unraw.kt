// port-lint: source impl/src/unraw.rs
package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.Ident
import io.github.kotlinmania.procmacro2.Literal
import io.github.kotlinmania.procmacro2.Span
import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.procmacro2.TokenTree
import io.github.kotlinmania.quote.ToTokens
import io.github.kotlinmania.quote.append
import io.github.kotlinmania.quote.toTokens

public data class Index(
    public val index: UInt,
    public val span: Span,
)

public class IdentUnraw(
    private val ident: Ident,
) : Comparable<IdentUnraw>, ToTokens {
    public companion object {
        public fun new(ident: Ident): IdentUnraw =
            IdentUnraw(ident)
    }

    public fun toLocal(): Ident {
        val repr = unrawText()
        val neverRaw = repr == "_" || repr == "super" || repr == "self" || repr == "Self" || repr == "crate"
        return if (!neverRaw && ident.toString().startsWith("r#")) {
            Ident.newRaw(repr, Span.callSite())
        } else {
            Ident.new(repr, ident.span())
        }
    }

    public fun setSpan(span: Span) {
        ident.setSpan(span)
    }

    internal fun span(): Span =
        ident.span()

    override fun compareTo(other: IdentUnraw): Int =
        unrawText().compareTo(other.unrawText())

    override fun toTokens(tokens: TokenStream) {
        toLocal().toTokens(tokens)
    }

    override fun equals(other: Any?): Boolean =
        when (other) {
            is IdentUnraw -> unrawText() == other.unrawText()
            is String -> unrawText() == other
            else -> false
        }

    override fun hashCode(): Int =
        unrawText().hashCode()

    override fun toString(): String =
        unrawText()

    private fun unrawText(): String =
        ident.toString().removePrefix("r#")
}

public sealed class MemberUnraw : ToTokens {
    public class Named(public val ident: IdentUnraw) : MemberUnraw()
    public class Unnamed(public val index: Index) : MemberUnraw()

    public fun span(): Span =
        when (this) {
            is Named -> ident.span()
            is Unnamed -> index.span
        }

    override fun toTokens(tokens: TokenStream) {
        when (this) {
            is Named -> ident.toTokens(tokens)
            is Unnamed -> tokens.append(TokenTree.Literal(Literal.u32Unsuffixed(index.index)))
        }
    }

    public fun contentEquals(other: String): Boolean =
        when (this) {
            is Named -> ident.toString() == other
            is Unnamed -> false
        }

    override fun toString(): String =
        when (this) {
            is Named -> ident.toString()
            is Unnamed -> index.index.toString()
        }

    override fun equals(other: Any?): Boolean =
        when {
            this is Named && other is Named -> ident == other.ident
            this is Unnamed && other is Unnamed -> index == other.index
            else -> false
        }

    override fun hashCode(): Int =
        when (this) {
            is Named -> ident.hashCode()
            is Unnamed -> index.hashCode()
        }
}
