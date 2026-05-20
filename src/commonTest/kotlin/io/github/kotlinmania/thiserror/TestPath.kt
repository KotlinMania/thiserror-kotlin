// port-lint: source tests/test_path.rs
package io.github.kotlinmania.thiserror

import kotlin.test.Test
import kotlin.test.assertEquals

class TestPath {
    private data class StructPathBuf(val file: String) : Error {
        override fun toString(): String = "failed to read '$file'"
    }

    private data class StructPath(val file: String) : Error {
        override fun toString(): String = "failed to read '$file'"
    }

    private sealed class EnumPathBuf : Error {
        data class Read(val file: String) : EnumPathBuf() {
            override fun toString(): String = "failed to read '$file'"
        }
    }

    private data class UnsizedError(val head: Int, val tail: String) : Error {
        override fun toString(): String = tail
    }

    private sealed class BothError : Error {
        data class DisplayDebug(val path: String) : BothError() {
            override fun toString(): String = "display:$path debug:$path"
        }

        data class DebugDisplay(val path: String) : BothError() {
            override fun toString(): String = "debug:$path display:$path"
        }
    }

    @Test
    fun testDisplay() {
        val path = "/thiserror"

        assertEquals("failed to read '/thiserror'", StructPathBuf(path).toString())
        assertEquals("failed to read '/thiserror'", EnumPathBuf.Read(path).toString())
        assertEquals("failed to read '/thiserror'", StructPath(path).toString())
        assertEquals("tail", UnsizedError(0, "tail").toString())
        assertEquals("display:/thiserror debug:/thiserror", BothError.DisplayDebug(path).toString())
        assertEquals("debug:/thiserror display:/thiserror", BothError.DebugDisplay(path).toString())
    }
}
