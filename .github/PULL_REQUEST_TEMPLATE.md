## Summary

What does this change and why?

## Type

- [ ] feat
- [ ] fix
- [ ] refactor
- [ ] docs / chore

## Checklist

- [ ] `mvn test` is green (unit + property + integration)
- [ ] Matcher coverage still 100%, `engine-core` ≥ 80%
- [ ] `engine-core` has no new runtime dependencies
- [ ] For hot-path changes: benchmark numbers before/after included below
- [ ] Conventional-commit messages

## Invariants verified (engine changes)

- [ ] Book never crosses
- [ ] Quantity conserved per trade
- [ ] FIFO / time-priority honored
- [ ] Deterministic replay

## Benchmark (if applicable)

```
before:
after:
```
