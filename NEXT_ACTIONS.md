# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 5/6 (83.3%)
- **Function parity:** 5/5 matched (target 17) — 100.0%
- **Class/type parity:** 8/9 matched (target 12) — 88.9%
- **Combined symbol parity:** 13/14 matched (target 29) — 92.9%
- **Average inline-code cosine:** 0.55 (function body across 5 matched files)
- **Average documentation cosine:** 0.00 (doc text across 5 matched files)
- **Cheat-zeroed Files:** 0
- **Critical Issues:** 3 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. var

- **Target:** `thiserror.Var`
- **Similarity:** 0.47
- **Dependents:** 1
- **Priority Score:** 1000205.2
- **Functions:** 1/1 matched (target 2)
- **Missing functions:** _none_
- **Types:** 1/1 matched
- **Missing types:** _none_

### 2. display

- **Target:** `thiserror.Display`
- **Similarity:** 0.33
- **Dependents:** 0
- **Priority Score:** 10606.7
- **Functions:** 2/2 matched (target 8)
- **Missing functions:** _none_
- **Types:** 3/4 matched (target 6)
- **Missing types:** `Placeholder`

### 3. aserror

- **Target:** `thiserror.Aserror`
- **Similarity:** 0.05
- **Dependents:** 0
- **Priority Score:** 309.5
- **Functions:** 1/1 matched
- **Missing functions:** _none_
- **Types:** 2/2 matched
- **Missing types:** _none_

### 4. provide

- **Target:** `thiserror.Provide`
- **Similarity:** 0.91
- **Dependents:** 0
- **Priority Score:** 300.9
- **Functions:** 1/1 matched (target 6)
- **Missing functions:** _none_
- **Types:** 2/2 matched (target 3)
- **Missing types:** _none_

### 5. private

- **Target:** `thiserror.Private`
- **Similarity:** 1.00
- **Dependents:** 0
- **Priority Score:** 0.0
- **Functions:** 0/0 matched
- **Missing functions:** _none_
- **Types:** 0/0 matched
- **Missing types:** _none_

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
| `lib` | `Lib` | 0 | `lib.rs` | `Lib.kt` |

