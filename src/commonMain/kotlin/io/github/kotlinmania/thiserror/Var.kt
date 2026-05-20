// port-lint: source var.rs
package io.github.kotlinmania.thiserror

/**
 * Wrapper used by generated formatting code when an interpolated value needs
 * pointer-style formatting.
 */
public data class Var<out T>(
    public val value: T,
) {
    /**
     * Formats the wrapped value using the supplied pointer formatter.
     */
    public fun formatPointer(formatter: PointerFormatter<T>): String = formatter.format(value)
}

/**
 * Formats the wrapper using the supplied pointer formatter.
 */
public fun <T> fmt(variable: Var<T>, formatter: PointerFormatter<T>): String =
    variable.formatPointer(formatter)

/**
 * Kotlin representation of the pointer formatter used by [Var].
 */
public fun interface PointerFormatter<in T> {
    public fun format(value: T): String
}
