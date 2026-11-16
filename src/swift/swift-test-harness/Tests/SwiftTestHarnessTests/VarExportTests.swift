import Testing
import Thiserror

@Suite("Thiserror formatting exports")
struct VarExportTests {
    @Test("Var forwards formatting options and Unicode output through Swift callbacks")
    func forwardsFormatter() {
        var written: [String] = []
        var calls = 0
        let writer = ExportedKotlinPackages.io.github.kotlinmania.thiserror.compat.fmt.write { value in
            written.append(value)
            return .Ok
        }
        let options = ExportedKotlinPackages.io.github.kotlinmania.thiserror.compat.fmt.FormattingOptions(
            sign: .Plus, signAwareZeroPad: true, alternate: true,
            fill: 0x1f642, align: .Center, width: 18, precision: 3, debugAsHex: .Upper
        )
        let formatter = options.createFormatter(write: writer)
        let pointer = ExportedKotlinPackages.io.github.kotlinmania.thiserror.compat.fmt.pointer { received in
            calls += 1
            #expect(received === formatter)
            #expect(received.fill() == 0x1f642)
            #expect(received.align() == .Center)
            #expect(received.width() == 18)
            #expect(received.precision() == 3)
            #expect(received.signPlus())
            #expect(!received.signMinus())
            #expect(received.alternate())
            #expect(received.signAwareZeroPad())
            #expect(received.options().debugAsHex == .Upper)
            return received.writeChar(value: 0x1f642)
        }
        let variable = ExportedKotlinPackages.io.github.kotlinmania.thiserror.Var(value: pointer)

        #expect(variable.value === pointer)
        #expect(variable.fmt(formatter: formatter) == .Ok)
        #expect(calls == 1)
        #expect(written == ["🙂"])
    }

    @Test("Var returns a Swift writer failure without retrying")
    func preservesWriterFailure() {
        var writes = 0
        var calls = 0
        let writer = ExportedKotlinPackages.io.github.kotlinmania.thiserror.compat.fmt.write { value in
            writes += 1
            #expect(value == "value")
            return .Err
        }
        let options = ExportedKotlinPackages.io.github.kotlinmania.thiserror.compat.fmt.FormattingOptions(
            sign: nil, signAwareZeroPad: false, alternate: false,
            fill: 32, align: nil, width: nil, precision: nil, debugAsHex: nil
        )
        let pointer = ExportedKotlinPackages.io.github.kotlinmania.thiserror.compat.fmt.pointer { formatter in
            calls += 1
            return formatter.writeStr(value: "value")
        }
        let variable = ExportedKotlinPackages.io.github.kotlinmania.thiserror.Var(value: pointer)

        #expect(variable.fmt(formatter: options.createFormatter(write: writer)) == .Err)
        #expect(calls == 1)
        #expect(writes == 1)
    }
}
