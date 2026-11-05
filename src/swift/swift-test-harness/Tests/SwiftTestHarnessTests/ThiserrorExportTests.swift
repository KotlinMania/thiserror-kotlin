import Testing
import Thiserror

@Suite("Thiserror Swift Export Tests")
struct ThiserrorExportTests {
    @Test("Exported display targets retain formatting semantics")
    func testDisplayTargetFormatting() {
        let target = DisplayTarget(value: nil)
        #expect(target.displayString() == "null")
        #expect(fmt(target: target) == "null")
    }
}

