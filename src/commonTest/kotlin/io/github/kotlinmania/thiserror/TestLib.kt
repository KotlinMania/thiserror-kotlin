// port-lint: source src/lib.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals

class TestLib {
    @Test
    fun deriveExportIsNamedError() {
        assertEquals(Error, Error)
        assertEquals(ThiserrorModule, ThiserrorModule)
    }
}
