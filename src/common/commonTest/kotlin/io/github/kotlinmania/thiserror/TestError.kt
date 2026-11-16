// port-lint: source tests/test_error.rs
package io.github.kotlinmania.thiserror

internal data class BracedError(
    val msg: String,
    val pos: Int,
) : StdError

internal data class TupleError(
    val message: String,
    val pos: Int,
) : StdError

internal class UnitError : StdError

internal data class WithSource(
    override val cause: StdError,
) : CausedStdError

internal data class WithAnyhow(
    override val cause: StdError,
) : CausedStdError

internal sealed class EnumError : StdError {
    data class Braced(
        override val cause: StdError,
    ) : EnumError(),
        CausedStdError

    data class Tuple(
        override val cause: StdError,
    ) : EnumError(),
        CausedStdError

    data object Unit : EnumError()
}
