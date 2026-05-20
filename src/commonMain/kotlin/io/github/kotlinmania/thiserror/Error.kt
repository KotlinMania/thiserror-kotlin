// port-lint: ignore
package io.github.kotlinmania.thiserror

// Shared Kotlin error surface re-homed from the crate and private helper exports.

/**
 * Kotlin standard error surface used by generated or handwritten thiserror
 * implementations.
 */
public interface Error : AsDynError, ThiserrorProvide {
    /**
     * The lower-level error that caused this error, if any.
     */
    public fun source(): Error? = null

    /**
     * Provides typed members such as a backtrace to the caller's request.
     */
    public fun provide(request: Request) {
        val source = source()
        if (source != null) {
            request.provideRef("source", source)
        }
    }

    override fun asDynError(): Error = this

    override fun thiserrorProvide(request: Request) {
        provide(request)
    }
}

/**
 * Backtrace value detected and carried by generated error implementations.
 */
public data class Backtrace(
    public val frames: String,
)
