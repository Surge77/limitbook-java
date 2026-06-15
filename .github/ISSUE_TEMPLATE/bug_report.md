---
name: Bug report
about: Report incorrect behavior (especially matcher correctness)
title: '[bug] '
labels: bug
---

## What happened

A clear description of the bug.

## Reproduction

For **matcher** bugs, paste the exact ordered sequence of commands (the engine is
deterministic, so this will reproduce every time):

```
submit LIMIT SELL price=100 qty=10
submit LIMIT BUY  price=100 qty=10
...
```

For gateway/frontend bugs, list the steps to reproduce.

## Expected vs actual

- Expected:
- Actual:

## Environment

- Module (engine-core / gateway / frontend):
- Java / Node version:
- OS:
