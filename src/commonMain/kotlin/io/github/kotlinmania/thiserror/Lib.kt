// port-lint: source lib.rs
package io.github.kotlinmania.thiserror

/**
 * Runtime helper surface for generated error implementations.
 *
 * This library mirrors the public crate whose derive macro generates standard
 * error implementations. Kotlin callers model their error types directly and
 * use this package's helper interfaces when generated or handwritten
 * implementations need source chaining, display adaptation, or provided
 * members such as a backtrace.
 *
 * Thiserror deliberately does not need to appear in a public error type. A
 * caller should get the same public shape as if the standard error surface had
 * been written by hand.
 */

// Module wiring is tracked from the upstream crate root. Kotlin keeps the
// helper declarations in direct package files and does not bridge the macro
// implementation through aliases.
