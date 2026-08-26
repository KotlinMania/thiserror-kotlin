// port-lint: source impl/src/valid.rs
package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.syn.GenericArgument
import io.github.kotlinmania.syn.PathArguments
import io.github.kotlinmania.syn.SynError
import io.github.kotlinmania.syn.SynType

public fun Input.validate() {
    when (this) {
        is Input.StructInput -> this.struct.validate()
        is Input.EnumInput -> this.enumVal.validate()
    }
}

public fun Struct.validate() {
    checkNonFieldAttrs(this.attrs)
    val transparent = this.attrs.transparent
    if (transparent != null) {
        if (this.fields.size != 1) {
            throw SynError.newSpanned(
                transparent.original,
                "#[error(transparent)] requires exactly one field",
            )
        }
        val source = this.fields.firstNotNullOfOrNull { it.attrs.source }
        if (source != null) {
            throw SynError.newSpanned(
                source.original,
                "transparent error struct can't contain #[source]",
            )
        }
    }
    val fmt = this.attrs.fmt
    if (fmt != null) {
        throw SynError.newSpanned(
            fmt.original,
            "#[error(fmt = ...)] is only supported in enums; for a struct, handwrite your own Display impl",
        )
    }
    checkFieldAttrs(this.fields)
    for (field in this.fields) {
        field.validate()
    }
}

public fun Enum.validate() {
    checkNonFieldAttrs(this.attrs)
    val hasDisplay = this.hasDisplay()
    for (variant in this.variants) {
        variant.validate()
        if (hasDisplay
            && variant.attrs.display == null
            && variant.attrs.transparent == null
            && variant.attrs.fmt == null
        ) {
            throw SynError.newSpanned(
                variant.original,
                "missing #[error(\"...\")] display attribute",
            )
        }
    }
}

public fun Variant.validate() {
    checkNonFieldAttrs(this.attrs)
    if (this.attrs.transparent != null) {
        if (this.fields.size != 1) {
            throw SynError.newSpanned(
                this.original,
                "#[error(transparent)] requires exactly one field",
            )
        }
        val source = this.fields.firstNotNullOfOrNull { it.attrs.source }
        if (source != null) {
            throw SynError.newSpanned(
                source.original,
                "transparent variant can't contain #[source]",
            )
        }
    }
    checkFieldAttrs(this.fields)
    for (field in this.fields) {
        field.validate()
    }
}

public fun Field.validate() {
    val unexpectedDisplayAttr = when {
        this.attrs.display != null -> this.attrs.display!!.original
        this.attrs.fmt != null -> this.attrs.fmt!!.original
        else -> null
    }
    if (unexpectedDisplayAttr != null) {
        throw SynError.newSpanned(
            unexpectedDisplayAttr,
            "not expected here; the #[error(...)] attribute belongs on top of a struct or an enum variant",
        )
    }
}

private fun checkNonFieldAttrs(attrs: Attrs) {
    val from = attrs.from
    if (from != null) {
        throw SynError.newSpanned(
            from.original,
            "not expected here; the #[from] attribute belongs on a specific field",
        )
    }
    val source = attrs.source
    if (source != null) {
        throw SynError.newSpanned(
            source.original,
            "not expected here; the #[source] attribute belongs on a specific field",
        )
    }
    val backtrace = attrs.backtrace
    if (backtrace != null) {
        throw SynError.newSpanned(
            backtrace,
            "not expected here; the #[backtrace] attribute belongs on a specific field",
        )
    }
    if (attrs.transparent != null) {
        val display = attrs.display
        if (display != null) {
            throw SynError.newSpanned(
                display.original,
                "cannot have both #[error(transparent)] and a display attribute",
            )
        }
        val fmt = attrs.fmt
        if (fmt != null) {
            throw SynError.newSpanned(
                fmt.original,
                "cannot have both #[error(transparent)] and #[error(fmt = ...)]",
            )
        }
    } else if (attrs.display != null && attrs.fmt != null) {
        throw SynError.newSpanned(
            attrs.display!!.original,
            "cannot have both #[error(fmt = ...)] and a format arguments attribute",
        )
    }
}

private fun checkFieldAttrs(fields: List<Field>) {
    var fromField: Field? = null
    var sourceField: Field? = null
    var backtraceField: Field? = null
    var hasBacktrace = false

    for (field in fields) {
        val from = field.attrs.from
        if (from != null) {
            if (fromField != null) {
                throw SynError.newSpanned(
                    from.original,
                    "duplicate #[from] attribute",
                )
            }
            fromField = field
        }
        val source = field.attrs.source
        if (source != null) {
            if (sourceField != null) {
                throw SynError.newSpanned(
                    source.original,
                    "duplicate #[source] attribute",
                )
            }
            sourceField = field
        }
        val backtrace = field.attrs.backtrace
        if (backtrace != null) {
            if (backtraceField != null) {
                throw SynError.newSpanned(
                    backtrace,
                    "duplicate #[backtrace] attribute",
                )
            }
            backtraceField = field
            hasBacktrace = true
        }
        val transparent = field.attrs.transparent
        if (transparent != null) {
            throw SynError.newSpanned(
                transparent.original,
                "#[error(transparent)] needs to go outside the enum or struct, not on an individual field",
            )
        }
        hasBacktrace = hasBacktrace || field.isBacktrace()
    }

    if (fromField != null && sourceField != null) {
        if (fromField.member != sourceField.member) {
            throw SynError.newSpanned(
                fromField.attrs.from!!.original,
                "#[from] is only supported on the source field, not any other field",
            )
        }
    }

    if (fromField != null) {
        val maxExpectedFields = when (backtraceField) {
            null -> 1 + if (hasBacktrace) 1 else 0
            else -> 1 + if (fromField.member != backtraceField.member) 1 else 0
        }
        if (fields.size > maxExpectedFields) {
            throw SynError.newSpanned(
                fromField.attrs.from!!.original,
                "deriving From requires no fields other than source and backtrace",
            )
        }
    }

    val finalSourceField = sourceField ?: fromField
    if (finalSourceField != null) {
        if (containsNonStaticLifetime(finalSourceField.ty)) {
            throw SynError.newSpanned(
                finalSourceField.original.ty,
                "non-static lifetimes are not allowed in the source of an error, because std::error::Error requires the source is dyn Error + 'static",
            )
        }
    }
}

private fun containsNonStaticLifetime(ty: SynType): Boolean {
    return when (ty) {
        is SynType.Path -> {
            val last = ty.path.segments.toList().lastOrNull() ?: return false
            val bracketed = when (val args = last.arguments) {
                is PathArguments.AngleBracketed -> args
                else -> return false
            }
            for (arg in bracketed.args.toList()) {
                when (arg) {
                    is GenericArgument.TypeArg -> if (containsNonStaticLifetime(arg.type)) return true
                    is GenericArgument.LifetimeArg -> if (arg.lifetime.ident.toString() != "static") return true
                    else -> {}
                }
            }
            false
        }
        is SynType.Reference -> {
            val lifetime = ty.lifetime
            lifetime != null && lifetime.ident.toString() != "static"
        }
        else -> false
    }
}
