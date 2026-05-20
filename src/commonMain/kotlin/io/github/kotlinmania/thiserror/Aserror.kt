// port-lint: source aserror.rs
package io.github.kotlinmania.thiserror

/**
 * Hidden helper used by generated error implementations to view an error value
 * through the standard error surface.
 */
public sealed interface AsDynError {
    public fun asDynError(): Error

    public sealed interface Sealed
}

/**
 * Adapts an [Error] to the dynamic error surface.
 */
public fun asDynError(error: Error): Error = error
