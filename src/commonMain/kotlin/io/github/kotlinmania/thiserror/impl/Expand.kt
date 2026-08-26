// port-lint: source impl/src/expand.rs
package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.Ident
import io.github.kotlinmania.procmacro2.Span
import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.quote.formatIdent
import io.github.kotlinmania.quote.quote
import io.github.kotlinmania.quote.quoteSpanned
import io.github.kotlinmania.syn.DeriveInput
import io.github.kotlinmania.syn.GenericArgument
import io.github.kotlinmania.syn.GenericParam
import io.github.kotlinmania.syn.PathArguments
import io.github.kotlinmania.syn.SynError
import io.github.kotlinmania.syn.SynType
import io.github.kotlinmania.syn.token.SelfType

public fun derive(input: DeriveInput): TokenStream {
    return try {
        tryExpand(input)
    } catch (error: SynError) {
        expandFallback(input, error)
    }
}

private fun tryExpand(input: DeriveInput): TokenStream {
    val astInput = Input.fromSyn(input)
    astInput.validate()
    return when (astInput) {
        is Input.StructInput -> implStruct(astInput.struct)
        is Input.EnumInput -> implEnum(astInput.enumVal)
    }
}

private fun implStruct(input: Struct): TokenStream {
    val ty = callSiteIdent(input.ident)
    val split = input.generics.splitForImpl()
    val implGenerics = split.implGenerics
    val tyGenerics = split.typeGenerics
    val whereClause = split.whereClause
    val errorInferredBounds = InferredBounds()
    val privateIdent = Private

    val sourceBody: TokenStream? = if (input.attrs.transparent != null) {
        val transparentAttr = input.attrs.transparent!!
        val onlyField = input.fields[0]
        if (onlyField.containsGeneric) {
            errorInferredBounds.insert(onlyField.ty, quote("::thiserror::#private::Error", mapOf("private" to privateIdent)))
        }
        val member = onlyField.member
        quoteSpanned(
            transparentAttr.span,
            "::thiserror::#private::Error::source(self.#member.as_dyn_error())",
            mapOf("private" to privateIdent, "member" to member),
        )
    } else if (input.sourceField() != null) {
        val sourceField = input.sourceField()!!
        val source = sourceField.member
        if (sourceField.containsGeneric) {
            val unoptTy = unoptionalType(sourceField.ty)
            errorInferredBounds.insert(unoptTy, quote("::thiserror::#private::Error + 'static", mapOf("private" to privateIdent)))
        }
        val asref = if (typeIsOption(sourceField.ty)) {
            quoteSpanned(source.span(), ".as_ref()?")
        } else {
            TokenStream.new()
        }
        val dynError = quoteSpanned(
            sourceField.sourceSpan(),
            "self.#source #asref.as_dyn_error()",
            mapOf("source" to source, "asref" to asref),
        )
        quote("::core::option::Option::Some(#dynError)", mapOf("dynError" to dynError))
    } else {
        null
    }

    val sourceMethod = if (sourceBody != null) {
        quote(
            """
            fn source(&self) -> ::core::option::Option<&(dyn ::thiserror::#private::Error + 'static)> {
                use ::thiserror::#private::AsDynError as _;
                #sourceBody
            }
            """,
            mapOf("private" to privateIdent, "sourceBody" to sourceBody),
        )
    } else {
        TokenStream.new()
    }

    val provideMethod = if (input.backtraceField() != null) {
        val backtraceField = input.backtraceField()!!
        val request = quote("request")
        val backtrace = backtraceField.member
        val body = if (input.sourceField() != null) {
            val sourceField = input.sourceField()!!
            val source = sourceField.member
            val sourceProvide = if (typeIsOption(sourceField.ty)) {
                quoteSpanned(
                    source.span(),
                    """
                    if let ::core::option::Option::Some(source) = &self.#source {
                        source.thiserror_provide(#request);
                    }
                    """,
                    mapOf("source" to source, "request" to request),
                )
            } else {
                quoteSpanned(
                    source.span(),
                    "self.#source.thiserror_provide(#request);",
                    mapOf("source" to source, "request" to request),
                )
            }
            val selfProvide = if (source == backtrace) {
                TokenStream.new()
            } else if (typeIsOption(backtraceField.ty)) {
                quote(
                    """
                    if let ::core::option::Option::Some(backtrace) = &self.#backtrace {
                        #request.provide_ref::<::thiserror::#private::Backtrace>(backtrace);
                    }
                    """,
                    mapOf("backtrace" to backtrace, "request" to request, "private" to privateIdent),
                )
            } else {
                quote(
                    "#request.provide_ref::<::thiserror::#private::Backtrace>(&self.#backtrace);",
                    mapOf("request" to request, "private" to privateIdent, "backtrace" to backtrace),
                )
            }
            quote(
                """
                use ::thiserror::#private::ThiserrorProvide as _;
                #sourceProvide
                #selfProvide
                """,
                mapOf("private" to privateIdent, "sourceProvide" to sourceProvide, "selfProvide" to selfProvide),
            )
        } else if (typeIsOption(backtraceField.ty)) {
            quote(
                """
                if let ::core::option::Option::Some(backtrace) = &self.#backtrace {
                    #request.provide_ref::<::thiserror::#private::Backtrace>(backtrace);
                }
                """,
                mapOf("backtrace" to backtrace, "request" to request, "private" to privateIdent),
            )
        } else {
            quote(
                "#request.provide_ref::<::thiserror::#private::Backtrace>(&self.#backtrace);",
                mapOf("request" to request, "private" to privateIdent, "backtrace" to backtrace),
            )
        }
        quote(
            """
            fn provide<'_request>(&'_request self, #request: &mut ::core::error::Request<'_request>) {
                #body
            }
            """,
            mapOf("request" to request, "body" to body),
        )
    } else {
        TokenStream.new()
    }

    val displayImpliedBounds = mutableSetOf<ImpliedBound>()
    val displayBody: TokenStream? = if (input.attrs.transparent != null) {
        val onlyField = input.fields[0].member
        displayImpliedBounds.add(ImpliedBound(0, Trait.Display))
        quote("::core::fmt::Display::fmt(&self.#onlyField, __formatter)", mapOf("onlyField" to onlyField))
    } else if (input.attrs.display != null) {
        val display = input.attrs.display!!
        displayImpliedBounds.addAll(display.impliedBounds)
        val useAsDisplayTokens = useAsDisplay(display.hasBonusDisplay) ?: TokenStream.new()
        val pat = fieldsPat(input.fields)
        quote(
            """
            #useAsDisplayTokens
            #[allow(unused_variables, deprecated)]
            let Self #pat = self;
            #display
            """,
            mapOf("useAsDisplayTokens" to useAsDisplayTokens, "pat" to pat, "display" to display),
        )
    } else {
        null
    }

    val displayImpl = if (displayBody != null) {
        val displayInferredBounds = InferredBounds()
        for (bound in displayImpliedBounds) {
            val field = input.fields[bound.index]
            if (field.containsGeneric) {
                displayInferredBounds.insert(field.ty, bound.trait)
            }
        }
        val displayWhereClause = displayInferredBounds.augmentWhereClause(input.generics)
        quote(
            """
            #[allow(unused_qualifications)]
            #[automatically_derived]
            impl #implGenerics ::core::fmt::Display for #ty #tyGenerics #displayWhereClause {
                #[allow(clippy::used_underscore_binding)]
                fn fmt(&self, __formatter: &mut ::core::fmt::Formatter) -> ::core::fmt::Result {
                    #displayBody
                }
            }
            """,
            mapOf(
                "implGenerics" to implGenerics,
                "ty" to ty,
                "tyGenerics" to tyGenerics,
                "displayWhereClause" to displayWhereClause,
                "displayBody" to displayBody,
            ),
        )
    } else {
        TokenStream.new()
    }

    val fromImpl = if (input.fromField() != null) {
        val fromField = input.fromField()!!
        val span = fromField.attrs.from!!.span
        val backtraceField = input.distinctBacktraceField()
        val fromType = unoptionalType(fromField.ty)
        val sourceVar = Ident.new("source", span)
        val body = fromInitializer(fromField, backtraceField, sourceVar)
        val fromFunction = quote(
            """
            fn from(#sourceVar: #fromType) -> Self {
                #ty #body
            }
            """,
            mapOf("sourceVar" to sourceVar, "fromType" to fromType, "ty" to ty, "body" to body),
        )
        val fromImplTokens = quoteSpanned(
            span,
            """
            #[automatically_derived]
            impl #implGenerics ::core::convert::From<#fromType> for #ty #tyGenerics #whereClause {
                #fromFunction
            }
            """,
            mapOf(
                "implGenerics" to implGenerics,
                "fromType" to fromType,
                "ty" to ty,
                "tyGenerics" to tyGenerics,
                "whereClause" to (whereClause ?: TokenStream.new()),
                "fromFunction" to fromFunction,
            ),
        )
        val lintAllows = if (input.generics.params.any { it is GenericParam.LifetimeParam }) {
            quote("clippy::elidable_lifetime_names, clippy::needless_lifetimes,")
        } else {
            TokenStream.new()
        }
        quote(
            """
            #[allow(
                deprecated,
                unused_qualifications,
                #lintAllows
            )]
            #fromImplTokens
            """,
            mapOf("lintAllows" to lintAllows, "fromImplTokens" to fromImplTokens),
        )
    } else {
        TokenStream.new()
    }

    if (input.generics.params.any { it is GenericParam.TypeParam }) {
        val selfToken = SelfType.default()
        errorInferredBounds.insert(selfToken, Trait.Debug)
        errorInferredBounds.insert(selfToken, Trait.Display)
    }
    val errorWhereClause = errorInferredBounds.augmentWhereClause(input.generics)

    return quote(
        """
        #[allow(unused_qualifications)]
        #[automatically_derived]
        impl #implGenerics ::thiserror::#private::Error for #ty #tyGenerics #errorWhereClause {
            #sourceMethod
            #provideMethod
        }
        #displayImpl
        #fromImpl
        """,
        mapOf(
            "implGenerics" to implGenerics,
            "private" to privateIdent,
            "ty" to ty,
            "tyGenerics" to tyGenerics,
            "errorWhereClause" to errorWhereClause,
            "sourceMethod" to sourceMethod,
            "provideMethod" to provideMethod,
            "displayImpl" to displayImpl,
            "fromImpl" to fromImpl,
        ),
    )
}

private fun implEnum(input: Enum): TokenStream {
    val ty = callSiteIdent(input.ident)
    val split = input.generics.splitForImpl()
    val implGenerics = split.implGenerics
    val tyGenerics = split.typeGenerics
    val whereClause = split.whereClause
    val errorInferredBounds = InferredBounds()
    val privateIdent = Private

    val sourceMethod = if (input.hasSource()) {
        val arms = input.variants.map { variant ->
            val ident = variant.ident
            if (variant.attrs.transparent != null) {
                val transparentAttr = variant.attrs.transparent!!
                val onlyField = variant.fields[0]
                if (onlyField.containsGeneric) {
                    errorInferredBounds.insert(onlyField.ty, quote("::thiserror::#private::Error", mapOf("private" to privateIdent)))
                }
                val member = onlyField.member
                val source = quoteSpanned(
                    transparentAttr.span,
                    "::thiserror::#private::Error::source(transparent.as_dyn_error())",
                    mapOf("private" to privateIdent),
                )
                quote("#ty::#ident {#member: transparent} => #source,", mapOf("ty" to ty, "ident" to ident, "member" to member, "source" to source))
            } else if (variant.sourceField() != null) {
                val sourceField = variant.sourceField()!!
                val source = sourceField.member
                if (sourceField.containsGeneric) {
                    val unoptTy = unoptionalType(sourceField.ty)
                    errorInferredBounds.insert(unoptTy, quote("::thiserror::#private::Error + 'static", mapOf("private" to privateIdent)))
                }
                val asref = if (typeIsOption(sourceField.ty)) {
                    quoteSpanned(source.span(), ".as_ref()?")
                } else {
                    TokenStream.new()
                }
                val varsource = quote("source")
                val dynError = quoteSpanned(
                    sourceField.sourceSpan(),
                    "#varsource #asref.as_dyn_error()",
                    mapOf("varsource" to varsource, "asref" to asref),
                )
                quote(
                    "#ty::#ident {#source: #varsource, ..} => ::core::option::Option::Some(#dynError),",
                    mapOf("ty" to ty, "ident" to ident, "source" to source, "varsource" to varsource, "dynError" to dynError),
                )
            } else {
                quote("#ty::#ident {..} => ::core::option::Option::None,", mapOf("ty" to ty, "ident" to ident))
            }
        }
        quote(
            """
            fn source(&self) -> ::core::option::Option<&(dyn ::thiserror::#private::Error + 'static)> {
                use ::thiserror::#private::AsDynError as _;
                #[allow(deprecated)]
                match self {
                    #(#arms)*
                }
            }
            """,
            mapOf("private" to privateIdent, "arms" to arms),
        )
    } else {
        TokenStream.new()
    }

    val provideMethod = if (input.hasBacktrace()) {
        val request = quote("request")
        val arms = input.variants.map { variant ->
            val ident = variant.ident
            val backtraceField = variant.backtraceField()
            val sourceField = variant.sourceField()
            if (backtraceField != null && sourceField != null && backtraceField.attrs.backtrace == null) {
                val backtrace = backtraceField.member
                val source = sourceField.member
                val varsource = quote("source")
                val sourceProvide = if (typeIsOption(sourceField.ty)) {
                    quoteSpanned(
                        source.span(),
                        """
                        if let ::core::option::Option::Some(source) = #varsource {
                            source.thiserror_provide(#request);
                        }
                        """,
                        mapOf("varsource" to varsource, "request" to request),
                    )
                } else {
                    quoteSpanned(
                        source.span(),
                        "#varsource.thiserror_provide(#request);",
                        mapOf("varsource" to varsource, "request" to request),
                    )
                }
                val selfProvide = if (typeIsOption(backtraceField.ty)) {
                    quote(
                        """
                        if let ::core::option::Option::Some(backtrace) = backtrace {
                            #request.provide_ref::<::thiserror::#private::Backtrace>(backtrace);
                        }
                        """,
                        mapOf("request" to request, "private" to privateIdent),
                    )
                } else {
                    quote(
                        "#request.provide_ref::<::thiserror::#private::Backtrace>(backtrace);",
                        mapOf("request" to request, "private" to privateIdent),
                    )
                }
                quote(
                    """
                    #ty::#ident {
                        #backtrace: backtrace,
                        #source: #varsource,
                        ..
                    } => {
                        use ::thiserror::#private::ThiserrorProvide as _;
                        #sourceProvide
                        #selfProvide
                    }
                    """,
                    mapOf(
                        "ty" to ty,
                        "ident" to ident,
                        "backtrace" to backtrace,
                        "source" to source,
                        "varsource" to varsource,
                        "private" to privateIdent,
                        "sourceProvide" to sourceProvide,
                        "selfProvide" to selfProvide,
                    ),
                )
            } else if (backtraceField != null && sourceField != null && backtraceField.member == sourceField.member) {
                val backtrace = backtraceField.member
                val varsource = quote("source")
                val sourceProvide = if (typeIsOption(sourceField.ty)) {
                    quoteSpanned(
                        backtrace.span(),
                        """
                        if let ::core::option::Option::Some(source) = #varsource {
                            source.thiserror_provide(#request);
                        }
                        """,
                        mapOf("varsource" to varsource, "request" to request),
                    )
                } else {
                    quoteSpanned(
                        backtrace.span(),
                        "#varsource.thiserror_provide(#request);",
                        mapOf("varsource" to varsource, "request" to request),
                    )
                }
                quote(
                    """
                    #ty::#ident {#backtrace: #varsource, ..} => {
                        use ::thiserror::#private::ThiserrorProvide as _;
                        #sourceProvide
                    }
                    """,
                    mapOf(
                        "ty" to ty,
                        "ident" to ident,
                        "backtrace" to backtrace,
                        "varsource" to varsource,
                        "private" to privateIdent,
                        "sourceProvide" to sourceProvide,
                    ),
                )
            } else if (backtraceField != null) {
                val backtrace = backtraceField.member
                val body = if (typeIsOption(backtraceField.ty)) {
                    quote(
                        """
                        if let ::core::option::Option::Some(backtrace) = backtrace {
                            #request.provide_ref::<::thiserror::#private::Backtrace>(backtrace);
                        }
                        """,
                        mapOf("request" to request, "private" to privateIdent),
                    )
                } else {
                    quote(
                        "#request.provide_ref::<::thiserror::#private::Backtrace>(backtrace);",
                        mapOf("request" to request, "private" to privateIdent),
                    )
                }
                quote(
                    "#ty::#ident {#backtrace: backtrace, ..} => { #body }",
                    mapOf("ty" to ty, "ident" to ident, "backtrace" to backtrace, "body" to body),
                )
            } else {
                quote("#ty::#ident {..} => {}", mapOf("ty" to ty, "ident" to ident))
            }
        }
        quote(
            """
            fn provide<'_request>(&'_request self, #request: &mut ::core::error::Request<'_request>) {
                #[allow(deprecated)]
                match self {
                    #(#arms)*
                }
            }
            """,
            mapOf("request" to request, "arms" to arms),
        )
    } else {
        TokenStream.new()
    }

    val displayImpl = if (input.hasDisplay()) {
        val displayInferredBounds = InferredBounds()
        val hasBonusDisplay = input.variants.any { it.attrs.display?.hasBonusDisplay == true }
        val useAsDisplayTokens = useAsDisplay(hasBonusDisplay) ?: TokenStream.new()
        val voidDeref = if (input.variants.isEmpty()) quote("*") else TokenStream.new()
        val arms = input.variants.map { variant ->
            val displayImpliedBounds = mutableSetOf<ImpliedBound>()
            val display = if (variant.attrs.display != null) {
                val disp = variant.attrs.display!!
                displayImpliedBounds.addAll(disp.impliedBounds)
                disp.toTokenStream()
            } else if (variant.attrs.fmt != null) {
                val fmtPath = variant.attrs.fmt!!.path
                val vars = variant.fields.map { field ->
                    when (val m = field.member) {
                        is MemberUnraw.Named -> m.ident.toLocal()
                        is MemberUnraw.Unnamed -> formatIdent("_{}", m.index.index)
                    }
                }
                quote("#fmtPath(#(#vars,)* __formatter)", mapOf("fmtPath" to fmtPath, "vars" to vars))
            } else {
                val onlyField = when (val m = variant.fields[0].member) {
                    is MemberUnraw.Named -> m.ident.toLocal()
                    is MemberUnraw.Unnamed -> formatIdent("_{}", m.index.index)
                }
                displayImpliedBounds.add(ImpliedBound(0, Trait.Display))
                quote("::core::fmt::Display::fmt(#onlyField, __formatter)", mapOf("onlyField" to onlyField))
            }
            for (bound in displayImpliedBounds) {
                val field = variant.fields[bound.index]
                if (field.containsGeneric) {
                    displayInferredBounds.insert(field.ty, bound.trait)
                }
            }
            val ident = variant.ident
            val pat = fieldsPat(variant.fields)
            quote("#ty::#ident #pat => #display", mapOf("ty" to ty, "ident" to ident, "pat" to pat, "display" to display))
        }
        val displayWhereClause = displayInferredBounds.augmentWhereClause(input.generics)
        quote(
            """
            #[allow(unused_qualifications)]
            #[automatically_derived]
            impl #implGenerics ::core::fmt::Display for #ty #tyGenerics #displayWhereClause {
                fn fmt(&self, __formatter: &mut ::core::fmt::Formatter) -> ::core::fmt::Result {
                    #useAsDisplayTokens
                    #[allow(unused_variables, deprecated, clippy::used_underscore_binding)]
                    match #voidDeref self {
                        #(#arms,)*
                    }
                }
            }
            """,
            mapOf(
                "implGenerics" to implGenerics,
                "ty" to ty,
                "tyGenerics" to tyGenerics,
                "displayWhereClause" to displayWhereClause,
                "useAsDisplayTokens" to useAsDisplayTokens,
                "voidDeref" to voidDeref,
                "arms" to arms,
            ),
        )
    } else {
        TokenStream.new()
    }

    val fromImpls = input.variants.mapNotNull { variant ->
        val fromField = variant.fromField() ?: return@mapNotNull null
        val span = fromField.attrs.from!!.span
        val backtraceField = variant.distinctBacktraceField()
        val variantIdent = variant.ident
        val fromType = unoptionalType(fromField.ty)
        val sourceVar = Ident.new("source", span)
        val body = fromInitializer(fromField, backtraceField, sourceVar)
        val fromFunction = quote(
            """
            fn from(#sourceVar: #fromType) -> Self {
                #ty::#variantIdent #body
            }
            """,
            mapOf("sourceVar" to sourceVar, "fromType" to fromType, "ty" to ty, "variantIdent" to variantIdent, "body" to body),
        )
        val fromImplTokens = quoteSpanned(
            span,
            """
            #[automatically_derived]
            impl #implGenerics ::core::convert::From<#fromType> for #ty #tyGenerics #whereClause {
                #fromFunction
            }
            """,
            mapOf(
                "implGenerics" to implGenerics,
                "fromType" to fromType,
                "ty" to ty,
                "tyGenerics" to tyGenerics,
                "whereClause" to (whereClause ?: TokenStream.new()),
                "fromFunction" to fromFunction,
            ),
        )
        val lintAllows = if (input.generics.params.any { it is GenericParam.LifetimeParam }) {
            quote("clippy::elidable_lifetime_names, clippy::needless_lifetimes,")
        } else {
            TokenStream.new()
        }
        quote(
            """
            #[allow(
                deprecated,
                unused_qualifications,
                #lintAllows
            )]
            #fromImplTokens
            """,
            mapOf("lintAllows" to lintAllows, "fromImplTokens" to fromImplTokens),
        )
    }

    if (input.generics.params.any { it is GenericParam.TypeParam }) {
        val selfToken = SelfType.default()
        errorInferredBounds.insert(selfToken, Trait.Debug)
        errorInferredBounds.insert(selfToken, Trait.Display)
    }
    val errorWhereClause = errorInferredBounds.augmentWhereClause(input.generics)

    return quote(
        """
        #[allow(unused_qualifications)]
        #[automatically_derived]
        impl #implGenerics ::thiserror::#private::Error for #ty #tyGenerics #errorWhereClause {
            #sourceMethod
            #provideMethod
        }
        #displayImpl
        #(#fromImpls)*
        """,
        mapOf(
            "implGenerics" to implGenerics,
            "private" to privateIdent,
            "ty" to ty,
            "tyGenerics" to tyGenerics,
            "errorWhereClause" to errorWhereClause,
            "sourceMethod" to sourceMethod,
            "provideMethod" to provideMethod,
            "displayImpl" to displayImpl,
            "fromImpls" to fromImpls,
        ),
    )
}

public fun callSiteIdent(ident: Ident): Ident {
    return Ident.new(ident.toString(), ident.span().resolvedAt(Span.callSite()))
}

private fun fieldsPat(fields: List<Field>): TokenStream {
    if (fields.isEmpty()) return quote("{}")
    return if (fields[0].member is MemberUnraw.Named) {
        val members = fields.map { it.member }
        quote("{ #(#members),* }", mapOf("members" to members))
    } else {
        val vars = fields.mapIndexed { index, _ -> formatIdent("_{}", index) }
        quote("( #(#vars),* )", mapOf("vars" to vars))
    }
}

private fun useAsDisplay(needsAsDisplay: Boolean): TokenStream? {
    return if (needsAsDisplay) {
        val privateIdent = Private
        quote(
            "use ::thiserror::#private::AsDisplay as _;",
            mapOf("private" to privateIdent),
        )
    } else {
        null
    }
}

private fun fromInitializer(
    fromField: Field,
    backtraceField: Field?,
    sourceVar: Ident,
): TokenStream {
    val fromMember = fromField.member
    val someSource = if (typeIsOption(fromField.ty)) {
        quote("::core::option::Option::Some(#sourceVar)", mapOf("sourceVar" to sourceVar))
    } else {
        quote("#sourceVar", mapOf("sourceVar" to sourceVar))
    }
    val privateIdent = Private
    val backtrace = if (backtraceField != null) {
        val backtraceMember = backtraceField.member
        if (typeIsOption(backtraceField.ty)) {
            quote(
                "#backtraceMember: ::core::option::Option::Some(::thiserror::#private::Backtrace::capture()),",
                mapOf("backtraceMember" to backtraceMember, "private" to privateIdent),
            )
        } else {
            quote(
                "#backtraceMember: ::core::convert::From::from(::thiserror::#private::Backtrace::capture()),",
                mapOf("backtraceMember" to backtraceMember, "private" to privateIdent),
            )
        }
    } else {
        TokenStream.new()
    }
    return quote(
        """
        {
            #fromMember: #someSource,
            #backtrace
        }
        """,
        mapOf("fromMember" to fromMember, "someSource" to someSource, "backtrace" to backtrace),
    )
}

public fun typeIsOption(ty: SynType): Boolean = typeParameterOfOption(ty) != null

public fun unoptionalType(ty: SynType): TokenStream {
    val unoptional = typeParameterOfOption(ty) ?: ty
    return quote("#unoptional", mapOf("unoptional" to unoptional))
}

public fun typeParameterOfOption(ty: SynType): SynType? {
    val path = when (ty) {
        is SynType.Path -> ty.path
        else -> return null
    }

    val last = path.segments.toList().lastOrNull() ?: return null
    if (last.ident.toString() != "Option") {
        return null
    }

    val bracketed = when (val args = last.arguments) {
        is PathArguments.AngleBracketed -> args
        else -> return null
    }

    val argsList = bracketed.args.toList()
    if (argsList.size != 1) {
        return null
    }

    return when (val arg0 = argsList.first()) {
        is GenericArgument.TypeArg -> arg0.type
        else -> null
    }
}
