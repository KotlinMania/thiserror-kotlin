import Foundation
import Testing
import Thiserror
import IoGithubKotlinmaniaProcMacro2Kotlin

@Suite("Source-map thread isolation")
struct SourceMapExportTests {
    @Test("Fresh native threads have independent source files")
    func freshThreadsHaveIndependentSourceFiles() async {
        for source in ["first", "second"] {
            let result: (String?, String?) = await withCheckedContinuation { continuation in
                Thread {
                    let parsed = ExportedKotlinPackages.io.github.kotlinmania.procmacro2.TokenStream.Companion.shared.fromString(src: source)
                    guard let tokens = parsed.value else {
                        continuation.resume(returning: (nil, nil))
                        return
                    }
                    let iterator = tokens.iterator()
                    guard iterator.hasNext() else {
                        continuation.resume(returning: (nil, nil))
                        return
                    }
                    let span = iterator.next().span()
                    continuation.resume(returning: (span.file(), span.sourceText()))
                }.start()
            }
            #expect(result.0 == "<parsed string 1>")
            #expect(result.1 == source)
        }
    }
}
