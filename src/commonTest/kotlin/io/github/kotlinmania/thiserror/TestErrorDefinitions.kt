// port-lint: source tests/test_error.rs
package io.github.kotlinmania.thiserror

private data class TestErrorBracedError(val msg: String, val pos: Int) : StdError {
    override fun toString(): String = "BracedError"
}

private data class TestErrorTupleError(val message: String, val pos: Int) : StdError {
    override fun toString(): String = "TupleError"
}

private class TestErrorUnitError : StdError {
    override fun toString(): String = "UnitError"
}

private data class TestErrorWithSource(val cause: StdError) : StdError {
    override fun source(): StdError = cause

    override fun toString(): String = "WithSource"
}

private data class TestErrorWithAnyhow(val cause: StdError) : StdError {
    override fun source(): StdError = cause

    override fun toString(): String = "WithAnyhow"
}

private sealed class TestErrorEnumError : StdError {
    data class Braced(val cause: StdError) : TestErrorEnumError() {
        override fun source(): StdError = cause

        override fun toString(): String = "Braced"
    }

    data class Tuple(val cause: StdError) : TestErrorEnumError() {
        override fun source(): StdError = cause

        override fun toString(): String = "Tuple"
    }

    data object Unit : TestErrorEnumError() {
        override fun toString(): String = "Unit"
    }
}
