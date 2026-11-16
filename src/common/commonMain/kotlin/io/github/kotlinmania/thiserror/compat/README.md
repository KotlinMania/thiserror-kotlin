# Formatting compatibility

`fmt/Formatting.kt` supplies the formatting contracts used by `Var`. Its source is
the installed Rust 1.99.0 `library/core/src/fmt/mod.rs`: `Alignment`, the fixed
formatting result, `Write`, `Sign`, `DebugAsHex`, `FormattingOptions`, `Formatter`,
and `Pointer`. The file contains the writer operations and option access needed
for forwarding; it does not implement a format-string interpreter or a pointer
memory model. The module-root source is split into this focused file without a
module-root provenance header or re-export aliases.

Kotlin strings and immutable option properties carry the values directly.
Unicode scalars use a checked `Int`, since a Kotlin `Char` cannot represent every
Unicode scalar. Width and precision retain their upstream 16-bit unsigned range.
`FmtResult.Ok` and `FmtResult.Err` are the two values of the fixed unit-success,
information-free-error result; no generic result or exception hierarchy is needed.
Writer failures and exceptions pass through unchanged. `withOptions` retains the
same underlying writer and leaves the original formatter's options unchanged.

`Var.fmt` follows `tmp/thiserror/src/var.rs`: the wrapped value receives the same
formatter exactly once, and its result is returned directly. Kotlin cannot make
interface conformance conditional on a generic argument, so the `Pointer` bound
is on `Var` itself. Consequently, an unformattable value cannot be placed in a
`Var`; upstream permits storing such a value but does not permit formatting it.
The value retains its concrete Kotlin type, and both Kotlin and Swift can call
the pointer operation. The earlier `PointerFormatter<T>` callback,
`formatPointer`, free `fmt` wrapper, and generated data-class operations were
not operations of the upstream `Var` and have been removed. This is a public API
correction, not a source-compatible alias migration.
