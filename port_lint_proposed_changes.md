# port-lint Proposed Changes

**Generated:** 2026-08-26
**Source:** tmp/thiserror
**Target:** src/commonMain/kotlin/io/github/kotlinmania/thiserror

These are review proposals only. They are emitted when a Rust -> Kotlin pair matches only after fallback normalization, so the existing `port-lint` header is not an exact provenance match.

| Target file | Current header | Proposed header | Source path | Reason |
|-------------|----------------|-----------------|-------------|--------|
| `src/commonMain/kotlin/io/github/kotlinmania/thiserror/Private.kt` | `// port-lint: source private.rs` | `// port-lint: source private.rs` | `private.rs` | `port-lint provenance header matched only after fallback normalization: 'private.rs' vs expected 'private.rs'` |
| `src/commonMain/kotlin/io/github/kotlinmania/thiserror/Display.kt` | `// port-lint: source display.rs` | `// port-lint: source display.rs` | `display.rs` | `port-lint provenance header matched only after fallback normalization: 'display.rs' vs expected 'display.rs'` |
| `src/commonMain/kotlin/io/github/kotlinmania/thiserror/Var.kt` | `// port-lint: source var.rs` | `// port-lint: source var.rs` | `var.rs` | `port-lint provenance header matched only after fallback normalization: 'var.rs' vs expected 'var.rs'` |
| `src/commonMain/kotlin/io/github/kotlinmania/thiserror/Lib.kt` | `// port-lint: source lib.rs` | `// port-lint: source lib.rs` | `lib.rs` | `port-lint provenance header matched only after fallback normalization: 'lib.rs' vs expected 'lib.rs'` |
| `src/commonMain/kotlin/io/github/kotlinmania/thiserror/Aserror.kt` | `// port-lint: source aserror.rs` | `// port-lint: source aserror.rs` | `aserror.rs` | `port-lint provenance header matched only after fallback normalization: 'aserror.rs' vs expected 'aserror.rs'` |
| `src/commonMain/kotlin/io/github/kotlinmania/thiserror/Provide.kt` | `// port-lint: source provide.rs` | `// port-lint: source provide.rs` | `provide.rs` | `port-lint provenance header matched only after fallback normalization: 'provide.rs' vs expected 'provide.rs'` |
