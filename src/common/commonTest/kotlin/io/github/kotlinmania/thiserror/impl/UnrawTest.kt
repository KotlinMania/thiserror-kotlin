// port-lint: source impl/src/unraw.rs
package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.Ident
import io.github.kotlinmania.procmacro2.Span
import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.syn.Index
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnrawTest {
    @Test
    fun namedMembersUseUnrawText() {
        val ident = IdentUnraw.new(Ident.newRaw("type", Span.callSite()))
        val member = MemberUnraw.Named(ident)

        assertEquals("type", ident.toString())
        assertEquals("type", member.toString())
        assertTrue(member.contentEquals("type"))
    }

    @Test
    fun unnamedMembersUseIndexText() {
        val member = MemberUnraw.Unnamed(Index(2u, Span.callSite()))

        assertEquals("2", member.toString())
        assertFalse(member.contentEquals("2"))
    }

    @Test
    fun tokenOutputUsesLocalIdentifier() {
        val ident = IdentUnraw.new(Ident.newRaw("type", Span.callSite()))
        val tokens = TokenStream.new()

        ident.toTokens(tokens)

        assertEquals("r#type", tokens.toString())
    }
}
