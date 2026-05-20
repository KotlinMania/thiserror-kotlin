// port-lint: source private.rs
package io.github.kotlinmania.thiserror

/**
 * Tracking file for the upstream private wiring module.
 *
 * The upstream module makes helper symbols available to generated error
 * implementations. Kotlin keeps those helpers as package-level declarations
 * rather than central alias bridges.
 */
