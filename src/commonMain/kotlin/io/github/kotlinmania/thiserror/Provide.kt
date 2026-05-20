// port-lint: source provide.rs
package io.github.kotlinmania.thiserror

/**
 * Request carrier for values provided by an error.
 */
public class Request {
    private val values: MutableMap<String, Any> = mutableMapOf()

    public fun provideRef(name: String, value: Any) {
        values[name] = value
    }

    public fun get(name: String): Any? = values[name]

    public fun contains(name: String): Boolean = values.containsKey(name)

    public fun provideBacktrace(backtrace: Backtrace) {
        provideRef("Backtrace", backtrace)
    }

    public fun backtrace(): Backtrace? = values["Backtrace"] as? Backtrace
}

/**
 * Hidden helper used by generated error implementations to forward provided
 * members into [Request].
 */
public sealed interface ThiserrorProvide {
    public fun thiserrorProvide(request: Request)

    public sealed interface Sealed
}

/**
 * Forwards provided members from an error into [Request].
 */
public fun thiserrorProvide(error: StdError, request: Request) {
    error.provide(request)
}
