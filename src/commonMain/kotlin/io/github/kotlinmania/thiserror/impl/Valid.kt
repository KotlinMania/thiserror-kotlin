// port-lint: source impl/src/valid.rs
package io.github.kotlinmania.thiserror.impl

import io.github.kotlinmania.syn.GenericArgument
import io.github.kotlinmania.syn.PathArguments
import io.github.kotlinmania.syn.SynError
import io.github.kotlinmania.syn.SynResult
import io.github.kotlinmania.syn.SynType

public fun Input.validate() {
    tryValidate().getOrThrow()
}

internal fun Input.tryValidate(): SynResult<Unit> {
    return when (this) {
        is Input.StructInput -> this.struct.tryValidate()
        is Input.EnumInput -> this.enumVal.tryValidate()
    }
}

public fun Struct.validate() {
    tryValidate().getOrThrow()
}

internal fun Struct.tryValidate(): SynResult<Unit> {
    checkNonFieldAttrs(this.attrs).getOrElse { return SynResult.failure(it) }
    val transparent = this.attrs.transparent
    if (transparent != null) {
        if (this.fields.size != 1) {
            return SynResult.failure(
                SynError.newSpanned(
                    transparent.original,
                    "#[error(transparent)] requires exactly one field",
                ),
            )
        }
        val source = this.fields.firstNotNullOfOrNull { it.attrs.source }
        if (source != null) {
            return SynResult.failure(
                SynError.newSpanned(
                    source.original,
                    "transparent error struct can't contain #[source]",
                ),
            )
        }
    }
    val fmt = this.attrs.fmt
    if (fmt != null) {
        return SynResult.failure(
            SynError.newSpanned(
                fmt.original,
                "#[error(fmt = ...)] is only supported in enums; for a struct, handwrite your own Display impl",
            ),
        )
    }
    checkFieldAttrs(this.fields).getOrElse { return SynResult.failure(it) }
    for (field in this.fields) {
        field.tryValidate().getOrElse { return SynResult.failure(it) }
    }
    return SynResult.success(Unit)
}

public fun Enum.validate() {
    tryValidate().getOrThrow()
}

internal fun Enum.tryValidate(): SynResult<Unit> {
    checkNonFieldAttrs(this.attrs).getOrElse { return SynResult.failure(it) }
    val hasDisplay = this.hasDisplay()
    for (variant in this.variants) {
        variant.tryValidate().getOrElse { return SynResult.failure(it) }
        if (hasDisplay &&
            variant.attrs.display == null &&
            variant.attrs.transparent == null &&
            variant.attrs.fmt == null
        ) {
            return SynResult.failure(
                SynError.newSpanned(
                    variant.original,
                    "missing #[error(\"...\")] display attribute",
                ),
            )
        }
    }
    return SynResult.success(Unit)
}

public fun Variant.validate() {
    tryValidate().getOrThrow()
}

internal fun Variant.tryValidate(): SynResult<Unit> {
    checkNonFieldAttrs(this.attrs).getOrElse { return SynResult.failure(it) }
    if (this.attrs.transparent != null) {
        if (this.fields.size != 1) {
            return SynResult.failure(
                SynError.newSpanned(
                    this.original,
                    "#[error(transparent)] requires exactly one field",
                ),
            )
        }
        val source = this.fields.firstNotNullOfOrNull { it.attrs.source }
        if (source != null) {
            return SynResult.failure(
                SynError.newSpanned(
                    source.original,
                    "transparent variant can't contain #[source]",
                ),
            )
        }
    }
    checkFieldAttrs(this.fields).getOrElse { return SynResult.failure(it) }
    for (field in this.fields) {
        field.tryValidate().getOrElse { return SynResult.failure(it) }
    }
    return SynResult.success(Unit)
}

public fun Field.validate() {
    tryValidate().getOrThrow()
}

internal fun Field.tryValidate(): SynResult<Unit> {
    val unexpectedDisplayAttr =
        when {
            this.attrs.display != null -> this.attrs.display!!.original
            this.attrs.fmt != null -> this.attrs.fmt!!.original
            else -> null
        }
    if (unexpectedDisplayAttr != null) {
        return SynResult.failure(
            SynError.newSpanned(
                unexpectedDisplayAttr,
                "not expected here; the #[error(...)] attribute belongs on top of a struct or an enum variant",
            ),
        )
    }
    return SynResult.success(Unit)
}

private fun checkNonFieldAttrs(attrs: Attrs): SynResult<Unit> {
    val from = attrs.from
    if (from != null) {
        return SynResult.failure(
            SynError.newSpanned(
                from.original,
                "not expected here; the #[from] attribute belongs on a specific field",
            ),
        )
    }
    val source = attrs.source
    if (source != null) {
        return SynResult.failure(
            SynError.newSpanned(
                source.original,
                "not expected here; the #[source] attribute belongs on a specific field",
            ),
        )
    }
    val backtrace = attrs.backtrace
    if (backtrace != null) {
        return SynResult.failure(
            SynError.newSpanned(
                backtrace,
                "not expected here; the #[backtrace] attribute belongs on a specific field",
            ),
        )
    }
    if (attrs.transparent != null) {
        val display = attrs.display
        if (display != null) {
            return SynResult.failure(
                SynError.newSpanned(
                    display.original,
                    "cannot have both #[error(transparent)] and a display attribute",
                ),
            )
        }
        val fmt = attrs.fmt
        if (fmt != null) {
            return SynResult.failure(
                SynError.newSpanned(
                    fmt.original,
                    "cannot have both #[error(transparent)] and #[error(fmt = ...)]",
                ),
            )
        }
    } else if (attrs.display != null && attrs.fmt != null) {
        return SynResult.failure(
            SynError.newSpanned(
                attrs.display!!.original,
                "cannot have both #[error(fmt = ...)] and a format arguments attribute",
            ),
        )
    }
    return SynResult.success(Unit)
}

private fun checkFieldAttrs(fields: List<Field>): SynResult<Unit> {
    var fromField: Field? = null
    var sourceField: Field? = null
    var backtraceField: Field? = null
    var hasBacktrace = false

    for (field in fields) {
        val from = field.attrs.from
        if (from != null) {
            if (fromField != null) {
                return SynResult.failure(
                    SynError.newSpanned(
                        from.original,
                        "duplicate #[from] attribute",
                    ),
                )
            }
            fromField = field
        }
        val source = field.attrs.source
        if (source != null) {
            if (sourceField != null) {
                return SynResult.failure(
                    SynError.newSpanned(
                        source.original,
                        "duplicate #[source] attribute",
                    ),
                )
            }
            sourceField = field
        }
        val backtrace = field.attrs.backtrace
        if (backtrace != null) {
            if (backtraceField != null) {
                return SynResult.failure(
                    SynError.newSpanned(
                        backtrace,
                        "duplicate #[backtrace] attribute",
                    ),
                )
            }
            backtraceField = field
            hasBacktrace = true
        }
        val transparent = field.attrs.transparent
        if (transparent != null) {
            return SynResult.failure(
                SynError.newSpanned(
                    transparent.original,
                    "#[error(transparent)] needs to go outside the enum or struct, not on an individual field",
                ),
            )
        }
        hasBacktrace = hasBacktrace || field.isBacktrace()
    }

    if (fromField != null && sourceField != null) {
        if (fromField.member != sourceField.member) {
            return SynResult.failure(
                SynError.newSpanned(
                    fromField.attrs.from!!.original,
                    "#[from] is only supported on the source field, not any other field",
                ),
            )
        }
    }

    if (fromField != null) {
        val maxExpectedFields =
            when (backtraceField) {
                null -> 1 + if (hasBacktrace) 1 else 0
                else -> 1 + if (fromField.member != backtraceField.member) 1 else 0
            }
        if (fields.size > maxExpectedFields) {
            return SynResult.failure(
                SynError.newSpanned(
                    fromField.attrs.from!!.original,
                    "deriving From requires no fields other than source and backtrace",
                ),
            )
        }
    }

    val finalSourceField = sourceField ?: fromField
    if (finalSourceField != null) {
        if (containsNonStaticLifetime(finalSourceField.ty)) {
            return SynResult.failure(
                SynError.newSpanned(
                    finalSourceField.original.ty,
                    "non-static lifetimes are not allowed in the source of an error, because std::error::Error requires the source is dyn Error + 'static",
                ),
            )
        }
    }
    return SynResult.success(Unit)
}

private fun containsNonStaticLifetime(ty: SynType): Boolean {
    return when (ty) {
        is SynType.Path -> {
            val last =
                ty.path.segments
                    .toList()
                    .lastOrNull() ?: return false
            val bracketed =
                when (val args = last.arguments) {
                    is PathArguments.AngleBracketed -> args
                    else -> return false
                }
            for (arg in bracketed.args.toList()) {
                when (arg) {
                    is GenericArgument.TypeArg -> {
                        if (containsNonStaticLifetime(arg.type)) return true
                    }

                    is GenericArgument.LifetimeArg -> {
                        if (arg.lifetime.ident.toString() != "static") return true
                    }

                    else -> {}
                }
            }
            false
        }

        is SynType.Reference -> {
            val lifetime = ty.lifetime
            lifetime != null && lifetime.ident.toString() != "static"
        }

        else -> {
            false
        }
    }
}
