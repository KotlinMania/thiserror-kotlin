// port-lint: source impl/src/prop.rs
package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.procmacro2.Span
import io.github.kotlinmania.syn.PathArguments
import io.github.kotlinmania.syn.SynType

public fun Struct.fromField(): Field? = fromField(this.fields)

public fun Struct.sourceField(): Field? = sourceField(this.fields)

public fun Struct.backtraceField(): Field? = backtraceField(this.fields)

public fun Struct.distinctBacktraceField(): Field? {
    val backtrace = this.backtraceField() ?: return null
    return distinctBacktraceField(backtrace, this.fromField())
}

public fun Enum.hasSource(): Boolean =
    this.variants.any { variant ->
        variant.sourceField() != null || variant.attrs.transparent != null
    }

public fun Enum.hasBacktrace(): Boolean =
    this.variants.any { variant ->
        variant.backtraceField() != null
    }

public fun Enum.hasDisplay(): Boolean =
    this.attrs.display != null
        || this.attrs.transparent != null
        || this.attrs.fmt != null
        || this.variants.any { variant -> variant.attrs.display != null || variant.attrs.fmt != null }
        || this.variants.all { variant -> variant.attrs.transparent != null }

public fun Variant.fromField(): Field? = fromField(this.fields)

public fun Variant.sourceField(): Field? = sourceField(this.fields)

public fun Variant.backtraceField(): Field? = backtraceField(this.fields)

public fun Variant.distinctBacktraceField(): Field? {
    val backtrace = this.backtraceField() ?: return null
    return distinctBacktraceField(backtrace, this.fromField())
}

public fun Field.isBacktrace(): Boolean = typeIsBacktrace(this.ty)

public fun Field.sourceSpan(): Span {
    val sourceAttr = this.attrs.source
    if (sourceAttr != null) {
        return sourceAttr.span
    }
    val fromAttr = this.attrs.from
    if (fromAttr != null) {
        return fromAttr.span
    }
    return this.member.span()
}

public fun fromField(fields: List<Field>): Field? {
    for (field in fields) {
        if (field.attrs.from != null) {
            return field
        }
    }
    return null
}

public fun sourceField(fields: List<Field>): Field? {
    for (field in fields) {
        if (field.attrs.from != null || field.attrs.source != null) {
            return field
        }
    }
    for (field in fields) {
        when (val member = field.member) {
            is MemberUnraw.Named -> {
                if (member.ident.toString() == "source") {
                    return field
                }
            }
            is MemberUnraw.Unnamed -> {}
        }
    }
    return null
}

public fun backtraceField(fields: List<Field>): Field? {
    for (field in fields) {
        if (field.attrs.backtrace != null) {
            return field
        }
    }
    for (field in fields) {
        if (field.isBacktrace()) {
            return field
        }
    }
    return null
}

public fun distinctBacktraceField(
    backtraceField: Field,
    fromField: Field?,
): Field? {
    return if (fromField != null && fromField.member == backtraceField.member) {
        null
    } else {
        backtraceField
    }
}

public fun typeIsBacktrace(ty: SynType): Boolean {
    val path = when (ty) {
        is SynType.Path -> ty.path
        else -> return false
    }

    val last = path.segments.toList().lastOrNull() ?: return false
    return last.ident.toString() == "Backtrace" && last.arguments is PathArguments.None
}
