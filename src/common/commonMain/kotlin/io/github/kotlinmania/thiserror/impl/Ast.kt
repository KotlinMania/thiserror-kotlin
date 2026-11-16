// port-lint: source impl/src/ast.rs
package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.Ident
import io.github.kotlinmania.procmacro2.Span
import io.github.kotlinmania.syn.Data
import io.github.kotlinmania.syn.DataEnum
import io.github.kotlinmania.syn.DataStruct
import io.github.kotlinmania.syn.DeriveInput
import io.github.kotlinmania.syn.Fields
import io.github.kotlinmania.syn.Generics
import io.github.kotlinmania.syn.Index
import io.github.kotlinmania.syn.SynError
import io.github.kotlinmania.syn.SynResult
import io.github.kotlinmania.syn.SynType
import io.github.kotlinmania.syn.Field as SynField
import io.github.kotlinmania.syn.Variant as SynVariant

public sealed class Input {
    public class StructInput(
        public val struct: Struct,
    ) : Input()

    public class EnumInput(
        public val enumVal: Enum,
    ) : Input()

    public companion object {
        public fun fromSyn(node: DeriveInput): Input =
            tryFromSyn(node).getOrThrow()

        internal fun tryFromSyn(node: DeriveInput): SynResult<Input> =
            when (val data = node.data) {
                is Data.Struct -> Struct.tryFromSyn(node, data.value).map { StructInput(it) }
                is Data.Enum -> Enum.tryFromSyn(node, data.value).map { EnumInput(it) }
                is Data.Union -> SynResult.failure(SynError.newSpanned(node, "union as errors are not supported"))
            }
    }
}

public class Struct(
    public var attrs: Attrs,
    public val ident: Ident,
    public val generics: Generics,
    public val fields: List<Field>,
) {
    public companion object {
        public fun fromSyn(node: DeriveInput, data: DataStruct): Struct =
            tryFromSyn(node, data).getOrThrow()

        internal fun tryFromSyn(node: DeriveInput, data: DataStruct): SynResult<Struct> {
            val attrs = getAttrs(node.attrs).getOrElse { return SynResult.failure(it) }
            val scope = ParamsInScope(node.generics)
            val fields = Field.tryMultipleFromSyn(data.fields, scope).getOrElse { return SynResult.failure(it) }
            val display = attrs.display
            if (display != null) {
                val container = ContainerKind.fromStruct(data)
                display.tryExpandShorthand(fields, container).getOrElse { return SynResult.failure(it) }
            }
            return SynResult.success(
                Struct(
                    attrs = attrs,
                    ident = node.ident,
                    generics = node.generics,
                    fields = fields,
                ),
            )
        }
    }
}

public class Enum(
    public var attrs: Attrs,
    public val ident: Ident,
    public val generics: Generics,
    public val variants: List<Variant>,
) {
    public companion object {
        public fun fromSyn(node: DeriveInput, data: DataEnum): Enum =
            tryFromSyn(node, data).getOrThrow()

        internal fun tryFromSyn(node: DeriveInput, data: DataEnum): SynResult<Enum> {
            val attrs = getAttrs(node.attrs).getOrElse { return SynResult.failure(it) }
            val scope = ParamsInScope(node.generics)
            val variants =
                data.variants.toList().map { variantNode ->
                    val variant = Variant.tryFromSyn(variantNode, scope).getOrElse { return SynResult.failure(it) }
                    if (variant.attrs.display == null && variant.attrs.transparent == null && variant.attrs.fmt == null) {
                        variant.attrs.display = attrs.display?.copy()
                        variant.attrs.transparent = attrs.transparent
                        variant.attrs.fmt = attrs.fmt?.copy()
                    }
                    val display = variant.attrs.display
                    if (display != null) {
                        val container = ContainerKind.fromVariant(variantNode)
                        display.tryExpandShorthand(variant.fields, container).getOrElse { return SynResult.failure(it) }
                    }
                    variant
                }
            return SynResult.success(
                Enum(
                    attrs = attrs,
                    ident = node.ident,
                    generics = node.generics,
                    variants = variants,
                ),
            )
        }
    }
}

public class Variant(
    public val original: SynVariant,
    public var attrs: Attrs,
    public val ident: Ident,
    public val fields: List<Field>,
) {
    public companion object {
        public fun fromSyn(node: SynVariant, scope: ParamsInScope): Variant =
            tryFromSyn(node, scope).getOrThrow()

        internal fun tryFromSyn(node: SynVariant, scope: ParamsInScope): SynResult<Variant> {
            val attrs = getAttrs(node.attrs).getOrElse { return SynResult.failure(it) }
            val fields = Field.tryMultipleFromSyn(node.fields, scope).getOrElse { return SynResult.failure(it) }
            return SynResult.success(
                Variant(
                    original = node,
                    attrs = attrs,
                    ident = node.ident,
                    fields = fields,
                ),
            )
        }
    }
}

public class Field(
    public val original: SynField,
    public var attrs: Attrs,
    public val member: MemberUnraw,
    public val ty: SynType,
    public val containsGeneric: Boolean,
) {
    public companion object {
        public fun multipleFromSyn(fields: Fields, scope: ParamsInScope): List<Field> =
            tryMultipleFromSyn(fields, scope).getOrThrow()

        internal fun tryMultipleFromSyn(fields: Fields, scope: ParamsInScope): SynResult<List<Field>> {
            val parsed = mutableListOf<Field>()
            for ((i, field) in fields.iter().withIndex()) {
                parsed.add(tryFromSyn(i, field, scope).getOrElse { return SynResult.failure(it) })
            }
            return SynResult.success(parsed)
        }

        public fun fromSyn(i: Int, node: SynField, scope: ParamsInScope): Field =
            tryFromSyn(i, node, scope).getOrThrow()

        internal fun tryFromSyn(i: Int, node: SynField, scope: ParamsInScope): SynResult<Field> {
            val attrs = getAttrs(node.attrs).getOrElse { return SynResult.failure(it) }
            val member =
                when (val name = node.ident) {
                    null -> MemberUnraw.Unnamed(Index(i.toUInt(), Span.callSite()))
                    else -> MemberUnraw.Named(IdentUnraw.new(name))
                }
            return SynResult.success(
                Field(
                    original = node,
                    attrs = attrs,
                    member = member,
                    ty = node.ty,
                    containsGeneric = scope.intersects(node.ty),
                ),
            )
        }
    }
}

public enum class ContainerKind {
    Struct,
    TupleStruct,
    UnitStruct,
    StructVariant,
    TupleVariant,
    UnitVariant,
    ;

    public companion object {
        public fun fromStruct(node: DataStruct): ContainerKind =
            when (node.fields) {
                is Fields.Named -> Struct
                is Fields.Unnamed -> TupleStruct
                is Fields.Unit -> UnitStruct
            }

        public fun fromVariant(node: SynVariant): ContainerKind =
            when (node.fields) {
                is Fields.Named -> StructVariant
                is Fields.Unnamed -> TupleVariant
                is Fields.Unit -> UnitVariant
            }
    }

    override fun toString(): String =
        when (this) {
            Struct -> "struct"
            TupleStruct -> "tuple struct"
            UnitStruct -> "unit struct"
            StructVariant -> "struct variant"
            TupleVariant -> "tuple variant"
            UnitVariant -> "unit variant"
        }
}
