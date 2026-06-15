# Security Policy

## Supported versions

This is a portfolio / educational project. Security fixes are applied to the
`main` branch only.

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Report privately via [GitHub Security Advisories](../../security/advisories/new),
or email **tejasdeshmane66@gmail.com** with:

- a description of the issue and its impact;
- steps to reproduce (for the engine, the exact order/command sequence);
- any suggested remediation.

You can expect an acknowledgement within a few days. Please allow reasonable time
for a fix before any public disclosure.

## Scope notes

- The matching `engine-core` performs no I/O and trusts only pre-validated input;
  all boundary validation happens in the gateway.
- The gateway restricts CORS to the configured frontend origin and validates the
  WebSocket `Origin` on upgrade. Do not deploy with `LIMITBOOK_CORS_ALLOWEDORIGINS=*`
  outside local/demo use.
- No secrets, credentials, or PII are stored or logged by this project.
