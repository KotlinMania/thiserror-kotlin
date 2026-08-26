// port-lint: source impl/src/fallback.rs
package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.TokenStream
import io.github.kotlinmania.quote.quote
import io.github.kotlinmania.syn.DeriveInput
import io.github.kotlinmania.syn.SynError

public fun expandFallback(input: DeriveInput, error: SynError): TokenStream {
    val ty = callSiteIdent(input.ident)
    val split = input.generics.splitForImpl()
    val implGenerics = split.implGenerics
    val tyGenerics = split.typeGenerics
    val whereClause = split.whereClause
    val errorCompiled = error.toCompileError()
    val privateIdent = Private

    return quote(
        """
        #errorCompiled

        #[allow(unused_qualifications)]
        #[automatically_derived]
        impl #implGenerics ::thiserror::#private::Error for #ty #tyGenerics #whereClause
        where
            for<'workaround> #ty #tyGenerics: ::core::fmt::Debug,
        {}

        #[allow(unused_qualifications)]
        #[automatically_derived]
        impl #implGenerics ::core::fmt::Display for #ty #tyGenerics #whereClause {
            fn fmt(&self, __formatter: &mut ::core::fmt::Formatter) -> ::core::fmt::Result {
                ::core::unreachable!()
            }
        }
        """,
        mapOf(
            "errorCompiled" to errorCompiled,
            "implGenerics" to implGenerics,
            "ty" to ty,
            "tyGenerics" to tyGenerics,
            "whereClause" to (whereClause ?: TokenStream.new()),
            "private" to privateIdent,
        ),
    )
}
