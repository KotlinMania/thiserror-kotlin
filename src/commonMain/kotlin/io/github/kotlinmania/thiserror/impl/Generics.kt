// port-lint: source impl/src/generics.rs
package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.quote.ToTokens
import io.github.kotlinmania.quote.toTokens
import io.github.kotlinmania.syn.GenericArgument
import io.github.kotlinmania.syn.Generics
import io.github.kotlinmania.syn.Ident
import io.github.kotlinmania.syn.PathArguments
import io.github.kotlinmania.syn.SynType
import io.github.kotlinmania.syn.TypeParamBound
import io.github.kotlinmania.syn.TypeParamBoundList
import io.github.kotlinmania.syn.TypeParamBoundParse
import io.github.kotlinmania.syn.WhereClause
import io.github.kotlinmania.syn.WherePredicate
import io.github.kotlinmania.syn.parse2
import io.github.kotlinmania.syn.token.Plus

public class ParamsInScope(generics: Generics) {
    private val names: Set<String> = generics.typeParams().map { it.ident.toString() }.toSet()

    public fun intersects(ty: SynType): Boolean {
        var found = false
        crawl(this, ty) { found = true }
        return found
    }

    internal fun contains(ident: Ident): Boolean =
        names.contains(ident.toString())
}

private fun crawl(inScope: ParamsInScope, ty: SynType, onFound: () -> Unit) {
    if (ty is SynType.Path) {
        val qself = ty.qself
        if (qself != null) {
            crawl(inScope, qself.ty, onFound)
        } else {
            val front = ty.path.segments.first()
            if (front != null && front.arguments is PathArguments.None && inScope.contains(front.ident)) {
                onFound()
            }
        }
        for (segment in ty.path.segments.toList()) {
            val args = segment.arguments
            if (args is PathArguments.AngleBracketed) {
                for (arg in args.args.toList()) {
                    if (arg is GenericArgument.TypeArg) {
                        crawl(inScope, arg.type, onFound)
                    }
                }
            }
        }
    }
}

public class InferredBounds {
    private val bounds: MutableMap<String, Pair<MutableSet<String>, TypeParamBoundList>> = mutableMapOf()
    private val order: MutableList<TokenStream> = mutableListOf()

    public fun insert(ty: ToTokens, bound: ToTokens) {
        val tyTokens = ty.toTokenStream()
        val boundTokens = bound.toTokenStream()
        val tyKey = tyTokens.toString()
        if (!bounds.containsKey(tyKey)) {
            order.add(tyTokens)
        }
        val entry: Pair<MutableSet<String>, TypeParamBoundList> =
            bounds.getOrPut(tyKey) { mutableSetOf<String>() to TypeParamBoundList() }
        val (set, boundList) = entry
        if (set.add(boundTokens.toString())) {
            val bound = parse2(TypeParamBoundParse, boundTokens).getOrThrow()
            boundList.push(bound, Plus::default)
        }
    }

    public fun augmentWhereClause(generics: Generics): WhereClause {
        val genericsCopy = generics.copy()
        val whereClause = genericsCopy.makeWhereClause()
        for (ty in order) {
            val boundsPair = bounds[ty.toString()]
            if (boundsPair != null) {
                val (_, boundList) = boundsPair
                whereClause.predicates.push(
                    WherePredicate.TypePredicate(
                        SynType.Verbatim(ty),
                        io.github.kotlinmania.syn.token.Colon.default(),
                        boundList.copy(),
                    ),
                    io.github.kotlinmania.syn.token.Comma::default,
                )
            }
        }
        return whereClause
    }
}
