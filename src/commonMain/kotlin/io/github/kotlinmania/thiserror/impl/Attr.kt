// port-lint: source impl/src/attr.rs
package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.Delimiter
import io.github.kotlinmania.procmacro2.Group
import io.github.kotlinmania.procmacro2.Ident
import io.github.kotlinmania.procmacro2.Literal
import io.github.kotlinmania.procmacro2.Punct
import io.github.kotlinmania.procmacro2.Spacing
import io.github.kotlinmania.procmacro2.Span
import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.procmacro2.TokenTree
import io.github.kotlinmania.quote.ToTokens
import io.github.kotlinmania.quote.formatIdent
import io.github.kotlinmania.quote.quote
import io.github.kotlinmania.quote.quoteSpanned
import io.github.kotlinmania.quote.toTokens
import io.github.kotlinmania.syn.AndPeek
import io.github.kotlinmania.syn.Attribute
import io.github.kotlinmania.syn.BracePeek
import io.github.kotlinmania.syn.BracketPeek
import io.github.kotlinmania.syn.BreakPeek
import io.github.kotlinmania.syn.CaretPeek
import io.github.kotlinmania.syn.CommaParse
import io.github.kotlinmania.syn.CommaPeek
import io.github.kotlinmania.syn.ContinuePeek
import io.github.kotlinmania.syn.DotParse
import io.github.kotlinmania.syn.DotPeek
import io.github.kotlinmania.syn.End
import io.github.kotlinmania.syn.EqParse
import io.github.kotlinmania.syn.EqPeek
import io.github.kotlinmania.syn.GroupPeek
import io.github.kotlinmania.syn.GtPeek
import io.github.kotlinmania.syn.IdentParse
import io.github.kotlinmania.syn.IdentPeek
import io.github.kotlinmania.syn.IfPeek
import io.github.kotlinmania.syn.InPeek
import io.github.kotlinmania.syn.LitFloatParse
import io.github.kotlinmania.syn.LitIntParse
import io.github.kotlinmania.syn.LitPeek
import io.github.kotlinmania.syn.LitStr
import io.github.kotlinmania.syn.LitStrParse
import io.github.kotlinmania.syn.LtPeek
import io.github.kotlinmania.syn.MatchPeek
import io.github.kotlinmania.syn.Meta
import io.github.kotlinmania.syn.MinusPeek
import io.github.kotlinmania.syn.MutPeek
import io.github.kotlinmania.syn.NotPeek
import io.github.kotlinmania.syn.OrPeek
import io.github.kotlinmania.syn.ParenPeek
import io.github.kotlinmania.syn.ParseStream
import io.github.kotlinmania.syn.Path
import io.github.kotlinmania.syn.PathParse
import io.github.kotlinmania.syn.PercentPeek
import io.github.kotlinmania.syn.PlusPeek
import io.github.kotlinmania.syn.ReturnPeek
import io.github.kotlinmania.syn.SemiPeek
import io.github.kotlinmania.syn.SlashPeek
import io.github.kotlinmania.syn.StarPeek
import io.github.kotlinmania.syn.SynError
import io.github.kotlinmania.syn.SynResult
import io.github.kotlinmania.syn.TokenTreeParse
import io.github.kotlinmania.syn.WhilePeek
import io.github.kotlinmania.syn.braced
import io.github.kotlinmania.syn.bracketed
import io.github.kotlinmania.syn.parenthesized

public class Attrs(
    public var display: Display? = null,
    public var source: Source? = null,
    public var backtrace: Attribute? = null,
    public var from: From? = null,
    public var transparent: Transparent? = null,
    public var fmt: Fmt? = null,
)

public data class ImpliedBound(public val index: Int, public val trait: Trait)

public data class DisplayBinding(public val name: Ident, public val expr: TokenStream)

public class Display(
    public val original: Attribute,
    public var fmt: LitStr,
    public var args: TokenStream,
    public var requiresFmtMachinery: Boolean,
    public var hasBonusDisplay: Boolean = false,
    public var infiniteRecursive: Boolean = false,
    public var impliedBounds: Set<ImpliedBound> = emptySet(),
    public var bindings: List<DisplayBinding> = emptyList(),
) : ToTokens {
    public fun copy(): Display =
        Display(
            original = original,
            fmt = fmt,
            args = args.clone(),
            requiresFmtMachinery = requiresFmtMachinery,
            hasBonusDisplay = hasBonusDisplay,
            infiniteRecursive = infiniteRecursive,
            impliedBounds = impliedBounds.toSet(),
            bindings = bindings.toList(),
        )

    override fun toTokens(tokens: TokenStream) {
        if (infiniteRecursive) {
            val span = fmt.span()
            quoteSpanned(
                span,
                """
                #[warn(unconditional_recursion)]
                fn _fmt() { _fmt() }
                """,
            ).toTokens(tokens)
        }

        val write = if (requiresFmtMachinery) {
            quote("::core::write!(__formatter, #fmt #args)", mapOf("fmt" to fmt, "args" to args))
        } else {
            quote("__formatter.write_str(#fmt)", mapOf("fmt" to fmt))
        }

        if (bindings.isEmpty()) {
            write.toTokens(tokens)
        } else {
            val locals = bindings.map { it.name }
            val values = bindings.map { it.expr }
            quote(
                """
                match (#(#values,)*) {
                    (#(#locals,)*) => #write
                }
                """,
                mapOf("values" to values, "locals" to locals, "write" to write),
            ).toTokens(tokens)
        }
    }
}

public enum class Trait(public val traitName: String) : ToTokens {
    Debug("Debug"),
    Display("Display"),
    Octal("Octal"),
    LowerHex("LowerHex"),
    UpperHex("UpperHex"),
    Pointer("Pointer"),
    Binary("Binary"),
    LowerExp("LowerExp"),
    UpperExp("UpperExp");

    override fun toTokens(tokens: TokenStream) {
        val ident = Ident.new(traitName, Span.callSite())
        val code = quote("::core::fmt::#ident", mapOf("ident" to ident))
        code.toTokens(tokens)
    }
}

public class Source(
    public val original: Attribute,
    public val span: Span,
)

public class From(
    public val original: Attribute,
    public val span: Span,
    public val source: Span,
)

public class Transparent(
    public val original: Attribute,
    public val span: Span,
)

public class Fmt(
    public val original: Attribute,
    public val path: Path,
) {
    public fun copy(): Fmt = Fmt(original, path)
}

public fun getAttrs(input: List<Attribute>): SynResult<Attrs> {
    val attrs = Attrs()
    for (attr in input) {
        if (attr.path().isIdent("error")) {
            parseErrorAttribute(attr, attrs).getOrElse { return SynResult.failure(it) }
        } else if (attr.path().isIdent("source")) {
            parseSourceAttribute(attr, attrs).getOrElse { return SynResult.failure(it) }
        } else if (attr.path().isIdent("backtrace")) {
            parseBacktraceAttribute(attr, attrs).getOrElse { return SynResult.failure(it) }
        } else if (attr.path().isIdent("from")) {
            parseFromAttribute(attr, attrs).getOrElse { return SynResult.failure(it) }
        }
    }
    return SynResult.success(attrs)
}

private fun parseErrorAttribute(attr: Attribute, attrs: Attrs): SynResult<Unit> {
    val meta = attr.meta
    return when (meta) {
        is Meta.List -> {
            attr.parseArgsWith { input ->
                parseErrorArgs(input, attr, attrs)
            }
        }
        else -> {
            SynResult.failure(
                SynError.newSpanned(attr, "expected #[error(...)] or #[error(\"...\")]"),
            )
        }
    }
}

private fun parseErrorArgs(input: ParseStream, attr: Attribute, attrs: Attrs): SynResult<Unit> {
    val isLitStr = input.peek(LitPeek) && input.fork().let { LitStrParse.parse(it).isSuccess }
    if (isLitStr) {
        val litStr = LitStrParse.parse(input).getOrElse { return SynResult.failure(it) }
        val args = if (input.isEmpty() || (input.peek(CommaPeek) && input.peek2(End))) {
            if (input.peek(CommaPeek)) {
                CommaParse.parse(input).getOrElse { return SynResult.failure(it) }
            }
            TokenStream.new()
        } else {
            parseTokenExpr(input, false).getOrElse { return SynResult.failure(it) }
        }
        val requiresFmtMachinery = !args.isEmpty()
        if (attrs.display != null) {
            return SynResult.failure(SynError.newSpanned(attr, "only one #[error(...)] attribute is allowed"))
        }
        attrs.display = Display(
            original = attr,
            fmt = litStr,
            args = args,
            requiresFmtMachinery = requiresFmtMachinery,
        )
        return SynResult.success(Unit)
    }

    if (input.peek(IdentPeek)) {
        val ident = IdentParse.parse(input).getOrElse { return SynResult.failure(it) }
        if (ident.toString() == "transparent") {
            if (attrs.transparent != null) {
                return SynResult.failure(SynError.newSpanned(attr, "duplicate #[error(transparent)] attribute"))
            }
            attrs.transparent = Transparent(attr, ident.span())
            return SynResult.success(Unit)
        } else if (ident.toString() == "fmt") {
            EqParse.parse(input).getOrElse { return SynResult.failure(it) }
            val path = PathParse.parse(input).getOrElse { return SynResult.failure(it) }
            if (attrs.fmt != null) {
                return SynResult.failure(SynError.newSpanned(attr, "duplicate #[error(fmt = ...)] attribute"))
            }
            attrs.fmt = Fmt(attr, path)
            return SynResult.success(Unit)
        }
    }

    return SynResult.failure(
        SynError.newSpanned(attr, "expected #[error(\"...\")] or #[error(transparent)] or #[error(fmt = ...)]"),
    )
}

private fun parseSourceAttribute(attr: Attribute, attrs: Attrs): SynResult<Unit> {
    if (attrs.source != null) {
        return SynResult.failure(SynError.newSpanned(attr, "duplicate #[source] attribute"))
    }
    attrs.source = Source(attr, attr.path().span())
    return SynResult.success(Unit)
}

private fun parseBacktraceAttribute(attr: Attribute, attrs: Attrs): SynResult<Unit> {
    if (attrs.backtrace != null) {
        return SynResult.failure(SynError.newSpanned(attr, "duplicate #[backtrace] attribute"))
    }
    attrs.backtrace = attr
    return SynResult.success(Unit)
}

private fun parseFromAttribute(attr: Attribute, attrs: Attrs): SynResult<Unit> {
    if (attrs.from != null) {
        return SynResult.failure(SynError.newSpanned(attr, "duplicate #[from] attribute"))
    }
    attrs.from = From(attr, attr.path().span(), attr.path().span())
    return SynResult.success(Unit)
}

private fun parseTokenExpr(input: ParseStream, mutBeginExpr: Boolean): SynResult<TokenStream> {
    var beginExpr = mutBeginExpr
    val tokens = mutableListOf<TokenTree>()
    while (!input.isEmpty()) {
        if (input.peek(GroupPeek)) {
            val parseRes = TokenTreeParse.parse(input)
            if (parseRes.isFailure) return SynResult.failure(parseRes.exceptionOrNull() ?: SynError.new(Span.callSite(), "parse error"))
            tokens.add(parseRes.getOrThrow())
            beginExpr = false
            continue
        }

        if (beginExpr && input.peek(DotPeek)) {
            if (input.peek2(IdentPeek)) {
                DotParse.parse(input).getOrElse { return SynResult.failure(it) }
                beginExpr = false
                continue
            } else if (input.peek2(LitPeek) && input.fork().let { DotParse.parse(it).isSuccess && LitIntParse.parse(it).isSuccess }) {
                DotParse.parse(input).getOrElse { return SynResult.failure(it) }
                val intLit = LitIntParse.parse(input).getOrElse { return SynResult.failure(it) }
                val indexVal = intLit.base10Digits().toUIntOrNull() ?: 0u
                val ident = formatIdent("_{}", indexVal)
                ident.setSpan(intLit.span())
                tokens.add(TokenTree.Ident(ident))
                beginExpr = false
                continue
            } else if (input.peek2(LitPeek) && input.fork().let { DotParse.parse(it).isSuccess && LitFloatParse.parse(it).isSuccess }) {
                val ahead = input.fork()
                DotParse.parse(ahead).getOrElse { return SynResult.failure(it) }
                val floatLit = LitFloatParse.parse(ahead).getOrElse { return SynResult.failure(it) }
                val repr = floatLit.toString()
                val parts = repr.split('.')
                if (parts.size == 2 && parts[0].toUIntOrNull() != null && parts[1].toUIntOrNull() != null) {
                    val first = parts[0].toUInt()
                    val second = parts[1].toUInt()
                    input.advanceTo(ahead)
                    val ident = formatIdent("_{}", first)
                    ident.setSpan(floatLit.span())
                    tokens.add(TokenTree.Ident(ident))
                    val punct = Punct('.', Spacing.Alone)
                    punct.setSpan(floatLit.span())
                    tokens.add(TokenTree.Punct(punct))
                    val literal = Literal.u32Unsuffixed(second)
                    literal.setSpan(floatLit.span())
                    tokens.add(TokenTree.Literal(literal))
                    beginExpr = false
                    continue
                }
            }
        }

        beginExpr = input.peek(BreakPeek)
            || input.peek(ContinuePeek)
            || input.peek(IfPeek)
            || input.peek(InPeek)
            || input.peek(MatchPeek)
            || input.peek(MutPeek)
            || input.peek(ReturnPeek)
            || input.peek(WhilePeek)
            || input.peek(PlusPeek)
            || input.peek(AndPeek)
            || input.peek(NotPeek)
            || input.peek(CaretPeek)
            || input.peek(CommaPeek)
            || input.peek(SlashPeek)
            || input.peek(EqPeek)
            || input.peek(GtPeek)
            || input.peek(LtPeek)
            || input.peek(OrPeek)
            || input.peek(PercentPeek)
            || input.peek(SemiPeek)
            || input.peek(StarPeek)
            || input.peek(MinusPeek)

        val token: TokenTree = if (input.peek(ParenPeek)) {
            val parens = parenthesized(input).getOrElse { return SynResult.failure(it) }
            val nested = parseTokenExpr(parens.content, true).getOrElse { return SynResult.failure(it) }
            parens.content.finishChildBuffer()
            val group = Group(Delimiter.Parenthesis, nested)
            group.setSpan(parens.token.span.join())
            TokenTree.Group(group)
        } else if (input.peek(BracePeek)) {
            val braces = braced(input).getOrElse { return SynResult.failure(it) }
            val nested = parseTokenExpr(braces.content, true).getOrElse { return SynResult.failure(it) }
            braces.content.finishChildBuffer()
            val group = Group(Delimiter.Brace, nested)
            group.setSpan(braces.token.span.join())
            TokenTree.Group(group)
        } else if (input.peek(BracketPeek)) {
            val brackets = bracketed(input).getOrElse { return SynResult.failure(it) }
            val nested = parseTokenExpr(brackets.content, true).getOrElse { return SynResult.failure(it) }
            brackets.content.finishChildBuffer()
            val group = Group(Delimiter.Bracket, nested)
            group.setSpan(brackets.token.span.join())
            TokenTree.Group(group)
        } else {
            val parseRes = TokenTreeParse.parse(input)
            if (parseRes.isFailure) return SynResult.failure(parseRes.exceptionOrNull() ?: SynError.new(Span.callSite(), "parse error"))
            parseRes.getOrThrow()
        }
        tokens.add(token)
    }
    return SynResult.success(TokenStream.fromTokenTrees(tokens))
}
