# port-lint Proposed Changes

**Generated:** 2026-05-20
**Source:** tmp/thiserror/impl/src
**Target:** src/commonMain/kotlin/io/github/kotlinmania/thiserror/impl

These are review proposals only. They are emitted when a Rust -> Kotlin pair matches only after fallback normalization, so the existing `port-lint` header is not an exact provenance match.

| Target file | Current header | Proposed header | Source path | Reason |
|-------------|----------------|-----------------|-------------|--------|
| `Unraw.kt` | `// port-lint: source impl/src/unraw.rs` | `// port-lint: source unraw.rs` | `unraw.rs` | `port-lint provenance header matched only after fallback normalization: 'impl/src/unraw.rs' vs expected 'unraw.rs'` |
