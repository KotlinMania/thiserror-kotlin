// port-lint: source impl/src/generics.rs
package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.quote.ToTokens
import io.github.kotlinmania.syn.Generics
import io.github.kotlinmania.syn.GenericParam
import io.github.kotlinmania.syn.Ident
import io.github.kotlinmania.syn.PathArguments
import io.github.kotlinmania.syn.SynType
import io.github.kotlinmania.syn.TypeParamBoundList
import io.github.kotlinmania.syn.TypeParamBoundParse
import io.github.kotlinmania.syn.WhereClause
import io.github.kotlinmania.syn.WherePredicate
import io.github.kotlinmania.syn.parse2
import io.github.kotlinmania.syn.token.Plus

public class ParamsInScope(private val names: Set<String>) {
    public constructor(generics: Generics) : this(
        generics.params.toList().filterIsInstance<GenericParam.TypeParam>().map { it.ident.toString() }.toSet()
    )
    public fun intersects(ty: SynType): Boolean {
        var found = false
        crawl(ty) { ident ->
            if (names.contains(ident.toString())) {
                found = true
            }
        }
        return found
    }

    private fun crawl(ty: SynType, visitor: (Ident) -> Unit) {
        when (ty) {
            is SynType.Path -> {
                if (ty.qself != null) {
                    crawl(ty.qself!!.ty, visitor)
                }
                for (segment in ty.path.segments.toList()) {
                    visitor(segment.ident)
                    crawlArguments(segment.arguments, visitor)
                }
            }
            is SynType.Reference -> crawl(ty.elem, visitor)
            is SynType.Paren -> crawl(ty.elem, visitor)
            is SynType.Group -> crawl(ty.elem, visitor)
            is SynType.Array -> crawl(ty.elem, visitor)
            is SynType.Slice -> crawl(ty.elem, visitor)
            is SynType.Tuple -> ty.elems.toList().forEach { crawl(it, visitor) }
            else -> {}
        }
    }

    private fun crawlArguments(args: PathArguments, visitor: (Ident) -> Unit) {
        when (args) {
            is PathArguments.AngleBracketed -> {
                for (arg in args.args.toList()) {
                    when (arg) {
                        is io.github.kotlinmania.syn.GenericArgument.TypeArg -> crawl(arg.type, visitor)
                        is io.github.kotlinmania.syn.GenericArgument.AssocTypeArg -> crawl(arg.assoc.ty, visitor)
                        else -> {}
                    }
                }
            }
            is PathArguments.Parenthesized -> {
                for (input in args.inputs.toList()) {
                    crawl(input, visitor)
                }
                when (val output = args.output) {
                    is io.github.kotlinmania.syn.ReturnType.TypeReturn -> crawl(output.ty, visitor)
                    else -> {}
                }
            }
            is PathArguments.None -> {}
        }
    }

    public companion object {
        public fun new(generics: Generics): ParamsInScope {
            val names = generics.params.mapNotNull { param ->
                when (param) {
                    is GenericParam.TypeParam -> param.ident.toString()
                    else -> null
                }
            }.toSet()
            return ParamsInScope(names)
        }
    }
}

public class InferredBounds {
    private val bounds: MutableMap<String, Pair<MutableSet<String>, TypeParamBoundList>> = mutableMapOf()
    private val order: MutableList<TokenStream> = mutableListOf()

    public fun insert(ty: Any, bound: Any) {
        val tyTokens = when (ty) {
            is ToTokens -> ty.toTokenStream()
            is TokenStream -> ty
            else -> TokenStream.fromString(ty.toString()).getOrThrow()
        }
        val boundTokens = when (bound) {
            is ToTokens -> bound.toTokenStream()
            is TokenStream -> bound
            else -> TokenStream.fromString(bound.toString()).getOrThrow()
        }
        val tyKey = tyTokens.toString()
        if (!bounds.containsKey(tyKey)) {
            order.add(tyTokens)
        }
        val entry: Pair<MutableSet<String>, TypeParamBoundList> =
            bounds.getOrPut(tyKey) { mutableSetOf<String>() to TypeParamBoundList() }
        val (set, boundList) = entry
        if (set.add(boundTokens.toString())) {
            val boundParsed = parse2(TypeParamBoundParse::parse, boundTokens).getOrThrow()
            boundList.push(boundParsed, Plus::default)
        }
    }

    public fun augmentWhereClause(generics: Generics): WhereClause {
        val genericsCopy = generics.copy()
        val whereClause = genericsCopy.makeWhereClause()
        for (ty in order) {
            val boundsPair = bounds[ty.toString()]
            if (boundsPair != null) {
                val (_, boundList) = boundsPair
                whereClause.predicates.pushValue(
                    WherePredicate.TypePredicate(
                        lifetimes = null,
                        boundedTy = SynType.Verbatim(ty),
                        colonToken = io.github.kotlinmania.syn.token.Colon.default(),
                        bounds = boundList.copy(),
                    ),
                )
            }
        }
        return whereClause
    }

    public companion object {
        public fun new(): InferredBounds = InferredBounds()
    }
}
