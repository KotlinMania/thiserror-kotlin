#if canImport(Testing)
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
#elseif canImport(XCTest)
import XCTest
import Thiserror

final class ThiserrorExportTests: XCTestCase {
    func testDisplayTargetFormatting() throws {
        let target = DisplayTarget(value: nil)
        XCTAssertEqual(target.displayString(), "null")
        XCTAssertEqual(fmt(target: target), "null")
    }
}
#endif
