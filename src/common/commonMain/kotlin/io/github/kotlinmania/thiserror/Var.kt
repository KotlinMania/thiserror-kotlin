// port-lint: source src/var.rs
package io.github.kotlinmania.thiserror

import io.github.kotlinmania.thiserror.compat.fmt.FmtResult
import io.github.kotlinmania.thiserror.compat.fmt.Formatter
import io.github.kotlinmania.thiserror.compat.fmt.Pointer

public class Var<out T : Pointer>(
    public val value: T,
) : Pointer {
    override fun fmt(formatter: Formatter): FmtResult = value.fmt(formatter)
}
