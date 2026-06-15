# Contributing to LimitBook

Thanks for your interest! This project values **correctness first** — the matcher
is the heart of the system, and a wrong matcher makes everything else worthless.

## Ground rules

- The `engine-core` module must stay dependency-free (no Spring, no network, no
  I/O) — only JUnit / jqwik / JMH at test scope. PRs that add runtime deps to the
  core will be rejected.
- Matcher changes are **test-first**. Write the failing test, then implement.
- Keep the matcher at 100% line coverage and `engine-core` ≥ 80%.

## Development

```bash
mvn test                     # unit + property + integration tests must be green
cd frontend && npm run build # frontend must type-check and build
```

Run the benchmark before/after any hot-path change and note the numbers in your
PR (see `engine-core/BENCHMARKS.md`).

## Pull requests

1. Branch from `main` (`feature/...` or `fix/...`).
2. One logical change per PR; keep commits in [Conventional Commits](https://www.conventionalcommits.org/) form
   (`feat(engine): ...`, `fix(gateway): ...`).
3. Ensure `mvn test` is green and coverage holds.
4. Describe what changed and, for engine changes, which invariants you verified.

## Reporting bugs

Use the issue templates. For matcher bugs, include the **exact order sequence**
that reproduces the wrong trade — determinism means it will reproduce every time.

## Code style

- Java: records/sealed types, integer-tick prices, no floats in the core, files
  under ~300 lines, comments explain *why* not *what*.
- TypeScript: named exports, no `any`, functional components, typed props.
