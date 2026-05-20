// port-lint: source tests/test_error.rs
package io.github.kotlinmania.thiserror

private data class TestErrorBracedError(val msg: String, val pos: Int) : Error {
    override fun toString(): String = "BracedError"
}

private data class TestErrorTupleError(val message: String, val pos: Int) : Error {
    override fun toString(): String = "TupleError"
}

private class TestErrorUnitError : Error {
    override fun toString(): String = "UnitError"
}

private data class TestErrorWithSource(val cause: Error) : Error {
    override fun source(): Error = cause

    override fun toString(): String = "WithSource"
}

private data class TestErrorWithAnyhow(val cause: Error) : Error {
    override fun source(): Error = cause

    override fun toString(): String = "WithAnyhow"
}

private sealed class TestErrorEnumError : Error {
    data class Braced(val cause: Error) : TestErrorEnumError() {
        override fun source(): Error = cause

        override fun toString(): String = "Braced"
    }

    data class Tuple(val cause: Error) : TestErrorEnumError() {
        override fun source(): Error = cause

        override fun toString(): String = "Tuple"
    }

    data object Unit : TestErrorEnumError() {
        override fun toString(): String = "Unit"
    }
}
