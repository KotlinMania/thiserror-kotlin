package io.github.kotlinmania.thiserror

// Shared Kotlin error surface re-homed from the crate and private helper exports.

/**
 * Kotlin standard-error surface used by generated or handwritten error
 * implementations.
 */
public interface StdError :
    AsDynError,
    ThiserrorProvide {
    /**
     * The lower-level error that caused this error, if any.
     */
    public fun source(): StdError? = null

    /**
     * Provides typed members such as a backtrace to the caller's request.
     */
    public fun provide(request: Request) {
        val source = source()
        if (source != null) {
            request.provideRef("source", source)
        }
    }

    override fun asDynError(): StdError = this

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
