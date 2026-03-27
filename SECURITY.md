# Security Policy

## Supported Versions

| Version | Supported |
|---|---|
| 2.3.x | Yes |
| < 2.3 | No |

## Reporting a Vulnerability

If you discover a security vulnerability in KAP, please report it responsibly:

1. **Do NOT open a public issue.**
2. Email **damian.lattenero@gmail.com** with:
   - Description of the vulnerability
   - Steps to reproduce
   - Impact assessment
3. You will receive a response within **48 hours**.
4. A fix will be developed privately and released as a patch version.

## Scope

KAP is a library that runs inside your application's coroutine scope. Security concerns are limited to:

- Ensuring `CancellationException` is never swallowed (could mask resource leaks)
- Resource cleanup guarantees in `bracket`/`Resource` (preventing connection leaks)
- Thread safety of `CircuitBreaker` and `memoize` (preventing race conditions)
- No reflection or runtime code generation (no injection surface)

## Acknowledgments

We appreciate responsible disclosure and will credit reporters in release notes (unless you prefer to remain anonymous).
