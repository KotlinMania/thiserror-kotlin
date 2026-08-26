# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 21/67 (31.3%)
- **Function parity:** 71/189 matched (target 314) — 37.6%
- **Class/type parity:** 68/148 matched (target 203) — 45.9%
- **Combined symbol parity:** 139/337 matched (target 517) — 41.2%
- **Average inline-code cosine:** 0.32 (function body across 19 matched files)
- **Average documentation cosine:** 0.00 (doc text across 19 matched files)
- **Cheat-zeroed Files:** 4
- **Critical Issues:** 18 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. private

- **Target:** `thiserror.Private [PROVENANCE-FALLBACK]`
- **Similarity:** 1.00
- **Dependents:** 3
- **Priority Score:** 3000000.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `private.rs` vs expected `private.rs`
- **Proposed provenance header:** `// port-lint: source private.rs` (current: `// port-lint: source private.rs`)
- **Lint issues:** 1

### 2. display

- **Target:** `thiserror.Display [STUB] [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 2
- **Priority Score:** 2000610.0
- **Functions:** 2/2 matched (target 11)
- **Missing functions:** _none_
- **Types:** 4/4 matched (target 7)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `display.rs` vs expected `display.rs`
- **Proposed provenance header:** `// port-lint: source display.rs` (current: `// port-lint: source display.rs`)
- **Lint issues:** 1

### 3. var

- **Target:** `thiserror.Var [PROVENANCE-FALLBACK]`
- **Similarity:** 0.47
- **Dependents:** 1
- **Priority Score:** 1000205.2
- **Functions:** 1/1 matched (target 2)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `var.rs` vs expected `var.rs`
- **Proposed provenance header:** `// port-lint: source var.rs` (current: `// port-lint: source var.rs`)
- **Lint issues:** 1

### 4. impl.unraw

- **Target:** `impl.Unraw`
- **Similarity:** 0.34
- **Dependents:** 0
- **Priority Score:** 61306.6
- **Functions:** 5/11 matched (target 19)
- **Missing functions:** `fmt`, `eq`, `cmp`, `partial_cmp`, `parse`, `hash`
- **Types:** 2/2 matched (target 5)
- **Missing types:** _none_

### 5. tests.test_error

- **Target:** `thiserror.TestErrorDefinitions [ZERO]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 60610.0
- **Functions:** 0/0 matched (target 12)
- **Missing functions:** _none_
- **Types:** 0/6 matched (target 9)
- **Missing types:** `BracedError`, `TupleError`, `UnitError`, `WithSource`, `WithAnyhow`, `EnumError`

### 6. tests.test_display

- **Target:** `thiserror.TestDisplay`
- **Similarity:** 0.26
- **Dependents:** 0
- **Priority Score:** 43707.4
- **Functions:** 32/34 matched (target 77)
- **Missing functions:** `assert`, `not`
- **Types:** 1/3 matched (target 47)
- **Missing types:** `Error`, `Struct`
- **Tests:** 29/29 matched

### 7. tests.test_expr

- **Target:** `thiserror.TestExpr`
- **Similarity:** 0.28
- **Dependents:** 0
- **Priority Score:** 40907.2
- **Functions:** 3/4 matched (target 10)
- **Missing functions:** `assert`
- **Types:** 2/5 matched (target 8)
- **Missing types:** `Trait`, `A`, `Error`
- **Tests:** 3/3 matched

### 8. tests.test_transparent

- **Target:** `thiserror.TestTransparent`
- **Similarity:** 0.34
- **Dependents:** 0
- **Priority Score:** 30806.6
- **Functions:** 5/5 matched (target 25)
- **Missing functions:** _none_
- **Types:** 0/3 matched (target 12)
- **Missing types:** `Error`, `ErrorKind`, `Any`
- **Tests:** 5/5 matched

### 9. impl.lib

- **Target:** `thiserror.Lib [ZERO] [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 30310.0
- **Functions:** 0/2 matched (target 0)
- **Missing functions:** `derive_error`, `to_tokens`
- **Types:** 0/1 matched (target 2)
- **Missing types:** `private`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `lib.rs` vs expected `lib.rs`
- **Proposed provenance header:** `// port-lint: source lib.rs` (current: `// port-lint: source lib.rs`)
- **Lint issues:** 1

### 10. tests.test_generics

- **Target:** `thiserror.TestGenerics`
- **Similarity:** 0.27
- **Dependents:** 0
- **Priority Score:** 21707.3
- **Functions:** 3/4 matched (target 25)
- **Missing functions:** `fmt`
- **Types:** 12/13 matched (target 23)
- **Missing types:** `Error`
- **Tests:** 3/3 matched

### 11. tests.test_backtrace

- **Target:** `thiserror.TestBacktrace`
- **Similarity:** 0.08
- **Dependents:** 0
- **Priority Score:** 11509.2
- **Functions:** 1/1 matched (target 43)
- **Missing functions:** _none_
- **Types:** 13/14 matched (target 24)
- **Missing types:** `Backtrace`
- **Tests:** 1/1 matched

### 12. tests.test_from

- **Target:** `thiserror.TestFrom`
- **Similarity:** 0.11
- **Dependents:** 0
- **Priority Score:** 10908.9
- **Functions:** 1/2 matched (target 18)
- **Missing functions:** `assert_impl`
- **Types:** 7/7 matched (target 13)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 13. tests.test_path

- **Target:** `thiserror.TestPath`
- **Similarity:** 0.27
- **Dependents:** 0
- **Priority Score:** 10707.3
- **Functions:** 1/2 matched (target 7)
- **Missing functions:** `assert`
- **Types:** 5/5 matched (target 9)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 14. impl.generics

- **Target:** `impl.Generics`
- **Similarity:** 0.51
- **Dependents:** 0
- **Priority Score:** 10704.9
- **Functions:** 4/5 matched
- **Missing functions:** `new`
- **Types:** 2/2 matched
- **Missing types:** _none_

### 15. tests.test_lints

- **Target:** `thiserror.TestLints`
- **Similarity:** 0.26
- **Dependents:** 0
- **Priority Score:** 1107.4
- **Functions:** 5/5 matched (target 17)
- **Missing functions:** _none_
- **Types:** 6/6 matched (target 12)
- **Missing types:** _none_
- **Tests:** 5/5 matched

### 16. tests.test_source

- **Target:** `thiserror.TestSource`
- **Similarity:** 0.62
- **Dependents:** 0
- **Priority Score:** 803.8
- **Functions:** 4/4 matched (target 14)
- **Missing functions:** _none_
- **Types:** 4/4 matched (target 8)
- **Missing types:** _none_
- **Tests:** 4/4 matched

### 17. tests.test_option

- **Target:** `thiserror.TestOption`
- **Similarity:** 0.06
- **Dependents:** 0
- **Priority Score:** 609.4
- **Functions:** 1/1 matched (target 19)
- **Missing functions:** _none_
- **Types:** 5/5 matched (target 13)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 18. aserror

- **Target:** `thiserror.Aserror [PROVENANCE-FALLBACK]`
- **Similarity:** 0.05
- **Dependents:** 0
- **Priority Score:** 309.5
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `aserror.rs` vs expected `aserror.rs`
- **Proposed provenance header:** `// port-lint: source aserror.rs` (current: `// port-lint: source aserror.rs`)
- **Lint issues:** 1

### 19. provide

- **Target:** `thiserror.Provide [PROVENANCE-FALLBACK]`
- **Similarity:** 0.91
- **Dependents:** 0
- **Priority Score:** 300.9
- **Functions:** 1/1 matched (target 6)
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `provide.rs` vs expected `provide.rs`
- **Proposed provenance header:** `// port-lint: source provide.rs` (current: `// port-lint: source provide.rs`)
- **Lint issues:** 1

### 20. tests.compiletest

- **Target:** `thiserror.Compiletest`
- **Similarity:** 0.19
- **Dependents:** 0
- **Priority Score:** 108.1
- **Functions:** 1/1 matched (target 2)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 2)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 21. lib

- **Target:** `thiserror.TestLib [STUB]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 10.0
- **Functions:** 0/0 matched (target 1)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 1)
- **Missing types:** _none_

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

