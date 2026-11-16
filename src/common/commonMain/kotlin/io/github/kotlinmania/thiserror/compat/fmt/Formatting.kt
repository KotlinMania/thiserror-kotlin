package io.github.kotlinmania.thiserror.compat.fmt

/** Possible alignments returned by [Formatter.align]. */
public enum class Alignment { Left, Right, Center }

/** The result of writing formatted text. */
public enum class FmtResult { Ok, Err }

/** Writes Unicode text into a buffer or stream. */
public fun interface Write {
    /** Succeeds only when the entire string has been written. */
    public fun writeStr(value: String): FmtResult

    /** Writes one Unicode scalar. */
    public fun writeChar(value: Int): FmtResult {
        require(value in 0..0x10ffff && value !in 0xd800..0xdfff)
        if (value < 0x10000) return writeStr(value.toChar().toString())
        val supplementary = value - 0x10000
        val high = (0xd800 + (supplementary shr 10)).toChar()
        val low = (0xdc00 + (supplementary and 0x3ff)).toChar()
        return writeStr("$high$low")
    }
}

/** The requested sign. */
public enum class Sign { Plus, Minus }

/** The hexadecimal mode requested for debug formatting. */
public enum class DebugAsHex { Lower, Upper }

/** Formatting options without an attached writer. */
public data class FormattingOptions(
    public val sign: Sign? = null,
    public val signAwareZeroPad: Boolean = false,
    public val alternate: Boolean = false,
    public val fill: Int = 32,
    public val align: Alignment? = null,
    public val width: Int? = null,
    public val precision: Int? = null,
    public val debugAsHex: DebugAsHex? = null,
) {
    init {
        require(fill in 0..0x10ffff && fill !in 0xd800..0xdfff)
        require(width == null || width in 0..65535)
        require(precision == null || precision in 0..65535)
    }

    public fun createFormatter(write: Write): Formatter = Formatter(write, this)
}

/** Configuration and destination for formatting. */
public class Formatter(
    private val buf: Write,
    private val formattingOptions: FormattingOptions = FormattingOptions(),
) : Write {
    public fun withOptions(options: FormattingOptions): Formatter = Formatter(buf, options)

    public fun fill(): Int = formattingOptions.fill

    public fun align(): Alignment? = formattingOptions.align

    public fun width(): Int? = formattingOptions.width

    public fun precision(): Int? = formattingOptions.precision

    public fun signPlus(): Boolean = formattingOptions.sign == Sign.Plus

    public fun signMinus(): Boolean = formattingOptions.sign == Sign.Minus

    public fun alternate(): Boolean = formattingOptions.alternate

    public fun signAwareZeroPad(): Boolean = formattingOptions.signAwareZeroPad

    public fun sign(): Sign? = formattingOptions.sign

    public fun options(): FormattingOptions = formattingOptions

    override fun writeStr(value: String): FmtResult = buf.writeStr(value)

    override fun writeChar(value: Int): FmtResult = buf.writeChar(value)
}

/** Formats a memory location, commonly presented as hexadecimal. */
public fun interface Pointer {
    public fun fmt(formatter: Formatter): FmtResult
}
