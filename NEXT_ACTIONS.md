# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 27/67 (40.3%)
- **Function parity:** 133/204 matched (target 400) — 65.2%
- **Class/type parity:** 89/147 matched (target 235) — 60.5%
- **Combined symbol parity:** 222/351 matched (target 635) — 63.2%
- **Average inline-code cosine:** 0.18 (function body across 27 matched files)
- **Average documentation cosine:** 0.00 (doc text across 27 matched files)
- **Cheat-zeroed Files:** 0
- **Critical Issues:** 25 files with <0.60 function similarity
- **Needs Review:** 0 files with 0.60-0.84 function similarity
- **Excellent:** 2 files with >=0.85 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. private

- **Target:** `thiserror.Private`
- **Similarity:** 1.00
- **Dependents:** 3
- **Priority Score:** 3000000.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 2. display

- **Target:** `thiserror.Display`
- **Similarity:** 0.02
- **Dependents:** 2
- **Priority Score:** 2020909.8
- **Functions:** 4/5 matched (target 8)
- **Missing functions:** `as_display`
- **Types:** 3/4 matched (target 6)
- **Missing types:** `Placeholder`

### 3. impl.scan_expr

- **Target:** `impl.ScanExpr`
- **Similarity:** 0.15
- **Dependents:** 1
- **Priority Score:** 1010308.6
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 1/2 matched (target 22)
- **Missing types:** `Input`

### 4. var

- **Target:** `thiserror.Var`
- **Similarity:** 0.26
- **Dependents:** 1
- **Priority Score:** 1000207.4
- **Functions:** 1/1 matched (target 11)
- **Missing functions:** _none_
- **Types:** 1/1 matched (target 3)
- **Missing types:** _none_

### 5. impl.fallback

- **Target:** `impl.Fallback`
- **Similarity:** 0.04
- **Dependents:** 1
- **Priority Score:** 1000109.6
- **Functions:** 1/1 matched (target 2)
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 6. impl.unraw

- **Target:** `impl.Unraw`
- **Similarity:** 0.15
- **Dependents:** 0
- **Priority Score:** 101808.5
- **Functions:** 6/16 matched (target 19)
- **Missing functions:** `fmt`, `eq`, `eq`, `cmp`, `partial_cmp`, `parse`, `fmt`, `eq`, `eq`, `hash`
- **Types:** 2/2 matched (target 5)
- **Missing types:** _none_

### 7. tests.test_display

- **Target:** `thiserror.TestDisplay`
- **Similarity:** 0.05
- **Dependents:** 0
- **Priority Score:** 53709.6
- **Functions:** 31/34 matched (target 74)
- **Missing functions:** `assert`, `not`, `test_pointer`
- **Types:** 1/3 matched (target 45)
- **Missing types:** `Error`, `Struct`
- **Tests:** 28/29 matched

### 8. tests.test_generics

- **Target:** `thiserror.TestGenerics`
- **Similarity:** 0.09
- **Dependents:** 0
- **Priority Score:** 41909.1
- **Functions:** 3/6 matched (target 25)
- **Missing functions:** `fmt`, `fmt`, `fmt`
- **Types:** 12/13 matched (target 23)
- **Missing types:** `Error`
- **Tests:** 3/3 matched

### 9. tests.test_expr

- **Target:** `thiserror.TestExpr`
- **Similarity:** 0.09
- **Dependents:** 0
- **Priority Score:** 40909.1
- **Functions:** 3/4 matched (target 10)
- **Missing functions:** `assert`
- **Types:** 2/5 matched (target 8)
- **Missing types:** `Trait`, `A`, `Error`
- **Tests:** 3/3 matched

### 10. aserror

- **Target:** `thiserror.Aserror`
- **Similarity:** 0.01
- **Dependents:** 0
- **Priority Score:** 40709.9
- **Functions:** 1/5 matched (target 1)
- **Missing functions:** `as_dyn_error`, `as_dyn_error`, `as_dyn_error`, `as_dyn_error`
- **Types:** 2/2 matched
- **Missing types:** _none_

### 11. tests.test_backtrace

- **Target:** `thiserror.TestBacktrace`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 31710.0
- **Functions:** 1/3 matched (target 47)
- **Missing functions:** `test_backtrace`, `test_backtrace`
- **Types:** 13/14 matched (target 24)
- **Missing types:** `Backtrace`
- **Tests:** 1/3 matched

### 12. tests.test_transparent

- **Target:** `thiserror.TestTransparent`
- **Similarity:** 0.18
- **Dependents:** 0
- **Priority Score:** 20808.2
- **Functions:** 5/5 matched (target 25)
- **Missing functions:** _none_
- **Types:** 1/3 matched (target 11)
- **Missing types:** `Error`, `ErrorKind`
- **Tests:** 5/5 matched

### 13. impl.ast

- **Target:** `impl.Ast`
- **Similarity:** 0.12
- **Dependents:** 0
- **Priority Score:** 11508.8
- **Functions:** 8/9 matched (target 15)
- **Missing functions:** `fmt`
- **Types:** 6/6 matched (target 8)
- **Missing types:** _none_

### 14. impl.attr

- **Target:** `impl.Attr`
- **Similarity:** 0.10
- **Dependents:** 0
- **Priority Score:** 11209.0
- **Functions:** 4/5 matched (target 11)
- **Missing functions:** `get`
- **Types:** 7/7 matched (target 9)
- **Missing types:** _none_

### 15. tests.test_from

- **Target:** `thiserror.TestFrom`
- **Similarity:** 0.02
- **Dependents:** 0
- **Priority Score:** 10909.8
- **Functions:** 1/2 matched (target 22)
- **Missing functions:** `assert_impl`
- **Types:** 7/7 matched (target 12)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 16. impl.fmt

- **Target:** `impl.Fmt`
- **Similarity:** 0.14
- **Dependents:** 0
- **Priority Score:** 10908.6
- **Functions:** 7/8 matched (target 9)
- **Missing functions:** `is_syn_full`
- **Types:** 1/1 matched
- **Missing types:** _none_

### 17. tests.test_path

- **Target:** `thiserror.TestPath`
- **Similarity:** 0.06
- **Dependents:** 0
- **Priority Score:** 10709.4
- **Functions:** 1/2 matched (target 7)
- **Missing functions:** `assert`
- **Types:** 5/5 matched (target 9)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 18. impl.prop

- **Target:** `impl.Prop`
- **Similarity:** 0.32
- **Dependents:** 0
- **Priority Score:** 1806.8
- **Functions:** 18/18 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 19. tests.test_lints

- **Target:** `thiserror.TestLints`
- **Similarity:** 0.01
- **Dependents:** 0
- **Priority Score:** 1109.9
- **Functions:** 5/5 matched (target 20)
- **Missing functions:** _none_
- **Types:** 6/6 matched (target 11)
- **Missing types:** _none_
- **Tests:** 5/5 matched

### 20. impl.expand

- **Target:** `impl.Expand`
- **Similarity:** 0.27
- **Dependents:** 0
- **Priority Score:** 1107.3
- **Functions:** 11/11 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 21. impl.generics

- **Target:** `impl.Generics`
- **Similarity:** 0.11
- **Dependents:** 0
- **Priority Score:** 808.9
- **Functions:** 6/6 matched (target 7)
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 22. impl.valid

- **Target:** `impl.Valid`
- **Similarity:** 0.11
- **Dependents:** 0
- **Priority Score:** 808.9
- **Functions:** 8/8 matched (target 13)
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

### 23. tests.test_source

- **Target:** `thiserror.TestSource`
- **Similarity:** 0.22
- **Dependents:** 0
- **Priority Score:** 807.8
- **Functions:** 4/4 matched (target 14)
- **Missing functions:** _none_
- **Types:** 4/4 matched (target 8)
- **Missing types:** _none_
- **Tests:** 4/4 matched

### 24. tests.test_option

- **Target:** `thiserror.TestOption`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 610.0
- **Functions:** 1/1 matched (target 22)
- **Missing functions:** _none_
- **Types:** 5/5 matched (target 12)
- **Missing types:** _none_
- **Tests:** 1/1 matched

### 25. tests.test_error

- **Target:** `thiserror.TestError`
- **Similarity:** 1.00
- **Dependents:** 0
- **Priority Score:** 600.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 6/6 matched (target 9)
- **Missing types:** _none_

### 26. provide

- **Target:** `thiserror.Provide`
- **Similarity:** 0.40
- **Dependents:** 0
- **Priority Score:** 306.0
- **Functions:** 1/1 matched (target 6)
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_

### 27. tests.compiletest

- **Target:** `thiserror.Compiletest`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 110.0
- **Functions:** 1/1 matched (target 2)
- **Missing functions:** _none_
- **Types:** 0/0 matched (target 2)
- **Missing types:** _none_
- **Tests:** 1/1 matched

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present

## Reexport / Wiring Modules

These files match `reexport_modules` patterns in `.ast_distance_config.json`. They are filtered out of
normal priority and missing-file ladders because they are wiring
modules, not direct logic ports. Consult them for call-site routing;
do not treat them as the next implementation target by default.

### Missing

| Source | Expected target | Deps | Source path | Expected path |
|--------|-----------------|------|-------------|---------------|
| `impl.lib` | `impl.src.Lib` | 0 | `impl/src/lib.rs` | `impl/src/Lib.kt` |
| `lib` | `Lib` | 0 | `src/lib.rs` | `Lib.kt` |

