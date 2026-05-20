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
public object Error

public object Thiserror
