// port-lint: source impl/src/fmt.rs
package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.Delimiter
import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.procmacro2.TokenTree
import io.github.kotlinmania.quote.formatIdent
import io.github.kotlinmania.quote.quote
import io.github.kotlinmania.quote.quoteSpanned
import io.github.kotlinmania.syn.CommaParse
import io.github.kotlinmania.syn.CommaPeek
import io.github.kotlinmania.syn.EqEqPeek
import io.github.kotlinmania.syn.EqParse
import io.github.kotlinmania.syn.EqPeek
import io.github.kotlinmania.syn.Ident
import io.github.kotlinmania.syn.Index
import io.github.kotlinmania.syn.LitStr
import io.github.kotlinmania.syn.ParseStream
import io.github.kotlinmania.syn.SynError
import io.github.kotlinmania.syn.SynResult
import io.github.kotlinmania.syn.TokenTreeParse
import io.github.kotlinmania.syn.parse2
import io.github.kotlinmania.syn.parseAny
import io.github.kotlinmania.syn.peekAny

public fun Display.expandShorthand(fields: List<Field>, container: ContainerKind) {
    val rawArgs = this.args
    val fmtArgs = explicitNamedArgs(rawArgs)

    val memberIndex = mutableMapOf<MemberUnraw, Int>()
    var extraPositionalArgumentsAllowed = true
    for ((i, field) in fields.withIndex()) {
        memberIndex[field.member] = i
        extraPositionalArgumentsAllowed = extraPositionalArgumentsAllowed && (field.member is MemberUnraw.Named)
    }

    val span = this.fmt.span()
    val fmtStr = this.fmt.value()
    var read = fmtStr
    val out = StringBuilder()
    var hasBonusDisplay = false
    var infiniteRecursive = false
    val impliedBounds = mutableSetOf<ImpliedBound>()
    val bindings = mutableListOf<DisplayBinding>()
    val macroNamedArgs = mutableSetOf<IdentUnraw>()

    this.requiresFmtMachinery = this.requiresFmtMachinery || fmtStr.contains('}')

    while (true) {
        val brace = read.indexOf('{')
        if (brace == -1) break

        this.requiresFmtMachinery = true
        out.append(read.substring(0, brace + 1))
        read = read.substring(brace + 1)
        if (read.startsWith('{')) {
            out.append('{')
            read = read.substring(1)
            continue
        }
        val next = read.firstOrNull() ?: return
        val member = when (next) {
            in '0'..'9' -> {
                val intStr = takeInt(read)
                read = read.substring(intStr.length)
                if (!extraPositionalArgumentsAllowed) {
                    val firstUnnamed = fmtArgs.firstUnnamed
                    if (firstUnnamed != null) {
                        val msg = "ambiguous reference to positional arguments by number in a $container; change this to a named argument"
                        throw SynError.newSpanned(firstUnnamed, msg)
                    }
                }
                val indexInt = intStr.toUIntOrNull() ?: return
                MemberUnraw.Unnamed(Index(indexInt, span))
            }
            in 'a'..'z', in 'A'..'Z', '_' -> {
                if (read.startsWith("r#")) {
                    continue
                }
                val repr = takeIdent(read)
                read = read.substring(repr.length)
                if (repr == "_") {
                    out.append(repr)
                    continue
                }
                val ident = IdentUnraw.new(io.github.kotlinmania.procmacro2.Ident.new(repr, span))
                if (fmtArgs.named.contains(ident)) {
                    out.append(repr)
                    continue
                }
                MemberUnraw.Named(ident)
            }
            else -> continue
        }

        val endSpec = read.indexOf('}')
        if (endSpec == -1) return
        var bonusDisplay = false
        val spec = read.substring(0, endSpec)
        val bound = when (spec.lastOrNull()) {
            '?' -> Trait.Debug
            'o' -> Trait.Octal
            'x' -> Trait.LowerHex
            'X' -> Trait.UpperHex
            'p' -> Trait.Pointer
            'b' -> Trait.Binary
            'e' -> Trait.LowerExp
            'E' -> Trait.UpperExp
            null -> {
                bonusDisplay = true
                hasBonusDisplay = true
                Trait.Display
            }
            else -> Trait.Display
        }

        infiniteRecursive = infiniteRecursive || (member.contentEquals("self") && bound == Trait.Display)
        val fieldIdx = memberIndex[member]
        if (fieldIdx == null) {
            out.append(member.toString())
            continue
        }

        impliedBounds.add(ImpliedBound(fieldIdx, bound))
        val formatvarPrefix = if (bonusDisplay) {
            "__display"
        } else if (bound == Trait.Pointer) {
            "__pointer"
        } else {
            "__field"
        }

        var formatvar = IdentUnraw.new(
            when (member) {
                is MemberUnraw.Unnamed -> formatIdent("{}{}", formatvarPrefix, member.index.index)
                is MemberUnraw.Named -> formatIdent("{}_{}", formatvarPrefix, member.ident.toString())
            },
        )
        while (fmtArgs.named.contains(formatvar)) {
            formatvar = IdentUnraw.new(formatIdent("_{}", formatvar.toString()))
        }
        formatvar.setSpan(span)
        out.append(formatvar.toString())
        if (!macroNamedArgs.add(formatvar)) {
            continue
        }

        val bindingValue = when (member) {
            is MemberUnraw.Unnamed -> formatIdent("_{}", member.index.index)
            is MemberUnraw.Named -> member.ident.toLocal()
        }
        bindingValue.setSpan(span.resolvedAt(fields[fieldIdx].member.span()))
        val private = Private
        val wrappedBindingValue = if (bonusDisplay) {
            quoteSpanned(span, "#bindingValue.as_display()", mapOf("bindingValue" to bindingValue))
        } else if (bound == Trait.Pointer) {
            quote("::thiserror::#private::Var(#bindingValue)", mapOf("private" to private, "bindingValue" to bindingValue))
        } else {
            TokenStream.fromTokenTree(TokenTree.Ident(bindingValue))
        }
        bindings.add(DisplayBinding(formatvar.toLocal(), wrappedBindingValue))
    }

    out.append(read)
    this.fmt = LitStr.new(out.toString(), this.fmt.span())
    this.hasBonusDisplay = hasBonusDisplay
    this.infiniteRecursive = infiniteRecursive
    this.impliedBounds = impliedBounds
    this.bindings = bindings
}

private class FmtArguments(
    val named: MutableSet<IdentUnraw>,
    var firstUnnamed: TokenStream?,
)

private fun explicitNamedArgs(rawArgs: TokenStream): FmtArguments {
    val parseRes = parse2(::explicitNamedArgsParse, rawArgs)
    return parseRes.getOrElse {
        FmtArguments(
            named = mutableSetOf(),
            firstUnnamed = null,
        )
    }
}

private fun explicitNamedArgsParse(input: ParseStream): SynResult<FmtArguments> {
    val ahead = input.fork()
    val tryRes = tryExplicitNamedArgs(ahead)
    if (tryRes.isSuccess) {
        input.advanceTo(ahead)
        return tryRes
    }

    val aheadFallback = input.fork()
    val fallbackRes = fallbackExplicitNamedArgs(aheadFallback)
    if (fallbackRes.isSuccess) {
        input.advanceTo(aheadFallback)
        return fallbackRes
    }

    while (!input.isEmpty()) {
        TokenTreeParse.parse(input).getOrElse { break }
    }
    return SynResult.success(
        FmtArguments(
            named = mutableSetOf(),
            firstUnnamed = null,
        ),
    )
}

private fun tryExplicitNamedArgs(input: ParseStream): SynResult<FmtArguments> {
    val args = FmtArguments(
        named = mutableSetOf(),
        firstUnnamed = null,
    )

    while (!input.isEmpty()) {
        val commaRes = CommaParse.parse(input)
        if (commaRes.isFailure) return SynResult.failure(commaRes.exceptionOrNull()!!)
        if (input.isEmpty()) {
            break
        }

        var beginUnnamed: ParseStream? = null
        if (input.peek(Ident.peekAny) && input.peek2(EqPeek) && !input.peek2(EqEqPeek)) {
            val identRes = Ident.parseAny(input)
            if (identRes.isFailure) return SynResult.failure(identRes.exceptionOrNull()!!)
            EqParse.parse(input).getOrElse { return SynResult.failure(it) }
            args.named.add(IdentUnraw.new(identRes.getOrThrow()))
        } else {
            beginUnnamed = input.fork()
        }

        val scanRes = scanExpr(input)
        if (scanRes.isFailure) return SynResult.failure(scanRes.exceptionOrNull()!!)

        if (beginUnnamed != null && args.firstUnnamed == null) {
            args.firstUnnamed = between(beginUnnamed, input)
        }
    }

    return SynResult.success(args)
}

private fun fallbackExplicitNamedArgs(input: ParseStream): SynResult<FmtArguments> {
    val args = FmtArguments(
        named = mutableSetOf(),
        firstUnnamed = null,
    )

    while (!input.isEmpty()) {
        if (input.peek(CommaPeek)
            && input.peek2(Ident.peekAny)
            && input.peek3(EqPeek)
            && !input.peek3(EqEqPeek)
        ) {
            CommaParse.parse(input).getOrElse { return SynResult.failure(it) }
            val identRes = Ident.parseAny(input)
            if (identRes.isFailure) return SynResult.failure(identRes.exceptionOrNull()!!)
            EqParse.parse(input).getOrElse { return SynResult.failure(it) }
            args.named.add(IdentUnraw.new(identRes.getOrThrow()))
        } else {
            val ttRes = TokenTreeParse.parse(input)
            if (ttRes.isFailure) return SynResult.failure(ttRes.exceptionOrNull()!!)
        }
    }

    return SynResult.success(args)
}

private fun takeInt(read: String): String {
    var len = 0
    for (ch in read) {
        if (ch in '0'..'9') len++ else break
    }
    return read.substring(0, len)
}

private fun takeIdent(read: String): String {
    var len = 0
    for (ch in read) {
        if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '_') len++ else break
    }
    return read.substring(0, len)
}

private fun between(begin: ParseStream, end: ParseStream): TokenStream {
    val endCursor = end.cursor()
    var cursor = begin.cursor()
    val tokens = mutableListOf<TokenTree>()

    while (true) {
        val cmp = cursor.partialCmp(endCursor) ?: break
        if (cmp >= 0) break

        val ttPair = cursor.tokenTree() ?: break
        val tt = ttPair.first
        val next = ttPair.second

        val nextCmp = next.partialCmp(endCursor)
        if (nextCmp != null && nextCmp > 0) {
            val group = cursor.group(Delimiter.None)
            if (group != null) {
                cursor = group.first
                continue
            }
            if (tokens.isEmpty()) {
                tokens.add(tt)
            }
            break
        }

        tokens.add(tt)
        cursor = next
    }

    return TokenStream.fromTokenTrees(tokens)
}
