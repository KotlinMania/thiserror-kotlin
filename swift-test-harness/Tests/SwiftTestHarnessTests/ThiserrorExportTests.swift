#if canImport(Testing)
import Testing
import Thiserror

@Suite("Thiserror Swift Export Tests")
struct ThiserrorExportTests {
    @Test("Swift module loads")
    func testSwiftModuleLoads() {
        #expect(Bool(true), "Thiserror swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import Thiserror

final class ThiserrorExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "Thiserror swift module imported cleanly")
    }
}
#endif
