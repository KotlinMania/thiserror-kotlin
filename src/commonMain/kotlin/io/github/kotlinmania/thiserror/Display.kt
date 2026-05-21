// port-lint: source display.rs
package io.github.kotlinmania.thiserror

/**
 * Hidden helper used by generated formatting code to borrow a value through a
 * displayable representation.
 */
public sealed interface AsDisplay {
    public fun asDisplay(): Target

    public sealed interface Sealed
}

/**
 * Display target returned by [AsDisplay].
 */
public sealed interface Target {
    public fun displayString(): String
}

/**
 * Display target backed by a Kotlin value.
 */
public data class DisplayTarget(
    public val value: Any?,
) : Target {
    override fun displayString(): String = value.toString()

    override fun toString(): String = displayString()
}

/**
 * Returns this value as its display target.
 */
public fun Any?.asDisplay(): Target = DisplayTarget(this)

/**
 * Display adapter for a value reference.
 */
public data class DisplayRef<out T>(
    public val value: T,
) : AsDisplay {
    override fun asDisplay(): Target = DisplayTarget(value)
}

/**
 * Synthetic second implementation that keeps generated-call inference from
 * depending on a single applicable display adapter.
 */
public object SyntheticDisplay : AsDisplay, Target {
    override fun asDisplay(): Target = this

    override fun displayString(): String = toString()

    override fun toString(): String = "SyntheticDisplay"
}

/**
 * Synthetic display implementation used when the general value adapter would
 * otherwise be the only applicable adapter.
 */
public object Placeholder : AsDisplay, Target {
    override fun asDisplay(): Target = this

    override fun displayString(): String = toString()

    override fun toString(): String = "Placeholder"
}

/**
 * Formats a display target to text.
 */
public fun fmt(target: Target): String = target.displayString()
