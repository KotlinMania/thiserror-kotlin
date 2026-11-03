import Testing
import Thiserror
import IoGithubKotlinmaniaProcMacro2Kotlin

@Suite("Thiserror derive exports")
struct DeriveErrorExportTests {
    private func expand(_ source: String) throws -> String {
        let parsed = ExportedKotlinPackages.io.github.kotlinmania.procmacro2.TokenStream.Companion.shared.fromString(src: source)
        #expect(!parsed.isFailure())
        let tokens = try #require(parsed.value)
        return Thiserror.impl.deriveError(input: tokens).toString()
            .split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
    }

    @Test("Struct expansion generates Error and Display implementations")
    func expandsStruct() throws {
        let output = try expand("#[error(\"problem\")] struct Valid;")
        #expect(output.contains("Error for Valid"))
        #expect(output.contains("Display for Valid"))
        #expect(output.contains("problem"))
        #expect(!output.contains("compile_error"))
    }

    @Test("Enum expansion retains both variants")
    func expandsEnum() throws {
        let output = try expand("#[error(\"problem\")] enum Valid { First, Second }")
        #expect(output.contains("Error for Valid"))
        #expect(output.contains("Display for Valid"))
        #expect(output.contains("First"))
        #expect(output.contains("Second"))
        #expect(!output.contains("compile_error"))
    }

    @Test("Structured validation diagnostics cross the Swift boundary")
    func preservesUnionDiagnosticAndFallback() throws {
        let output = try expand("union Invalid { field: u8 }")
        #expect(output.contains("compile_error"))
        #expect(output.contains("union as errors are not supported"))
        #expect(output.contains("Error for Invalid"))
        #expect(output.contains("Display for Invalid"))
    }

    @Test("Attribute validation preserves the first diagnostic")
    func preservesDiagnosticOrder() throws {
        let output = try expand("#[source] #[from] struct Invalid;")
        #expect(output.contains("not expected here; the #[from] attribute belongs on a specific field"))
        #expect(!output.contains("the #[source] attribute belongs"))
        #expect(output.contains("Error for Invalid"))
    }

    @Test("Syntax failures return parser diagnostics without generated implementations")
    func preservesParserFailure() throws {
        let output = try expand("struct")
        #expect(output.contains("compile_error"))
        #expect(!output.contains("automatically_derived"))
    }
}
