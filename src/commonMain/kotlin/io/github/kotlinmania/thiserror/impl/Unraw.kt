// port-lint: source impl/src/unraw.rs
package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.Ident
import io.github.kotlinmania.procmacro2.Span
import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.quote.ToTokens
import io.github.kotlinmania.quote.toTokens
import io.github.kotlinmania.syn.IdentParse
import io.github.kotlinmania.syn.Index
import io.github.kotlinmania.syn.parseStr
import io.github.kotlinmania.syn.unraw

public class IdentUnraw(
    private val ident: Ident,
) : Comparable<IdentUnraw>, ToTokens {
    public companion object {
        public fun new(ident: Ident): IdentUnraw =
            IdentUnraw(ident)
    }

    public fun toLocal(): Ident {
        val unraw = ident.unraw()
        val repr = unraw.toString()
        if (parseStr(IdentParse, repr).isFailure) {
            if (repr != "_" && repr != "super" && repr != "self" && repr != "Self" && repr != "crate") {
                return Ident.newRaw(repr, Span.callSite())
            }
        }
        return unraw
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
            is Unnamed -> index.toTokens(tokens)
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
