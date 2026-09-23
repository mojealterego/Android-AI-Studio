# Consent Runtime Release Gate

Status: **PHASE C IMPLEMENTED AND CI-VERIFIED**

This gate records the implemented consent enforcement boundary for the current single-backend-instance architecture. It is not a claim that the system is an enterprise identity platform.

## Verified controls

- [x] Normal API authentication remains required.
- [x] A separate approval credential is required for grant creation and generation dispatch.
- [x] The approver subject is derived server-side from the configured approval credential; clients cannot submit actor_id or subject_id.
- [x] The server exposes an approval preview containing workflow identity, version, parameters, scope and consequence.
- [x] Approval recomputes the exact preview digest and rejects tampering.
- [x] Workflow graphs are server-owned and validated through the registry.
- [x] Every v2 generation dispatch passes through the same consent guard immediately before the ComfyUI side effect.
- [x] Legacy arbitrary-graph dispatch remains disabled with HTTP 410.
- [x] Grant subject, resource, access, expiry, revocation and task/session scope are enforced.
- [x] ONCE grants are atomically consumed immediately before dispatch.
- [x] Ambiguous ComfyUI transport outcomes are recorded as UNKNOWN and the consumed grant is not replayed.
- [x] ComfyUI-rejected dispatches are recorded as FAILED.
- [x] Lifecycle receipts are append-only and legal transitions are enforced.
- [x] Receipt reads are actor-scoped at the API layer.
- [x] Receipts exclude bearer tokens, approval secrets and raw credentials.
- [x] Tampering, replay, wrong resource, wrong actor, approval authentication and ambiguous-outcome tests are covered by CI.
- [x] Exact release commit CI is green.

## Current architecture boundary

The local APPROVAL_TOKENS_JSON mechanism is a separate transaction-authorization credential, but it is not a complete enterprise identity provider. It does not provide identity proofing, MFA, federation or non-repudiation. A future multi-user production deployment must replace or wrap it with a stronger authenticated identity/transaction-authorization system.

The distinction matters: authentication and authorization are separate controls, and sensitive transaction authorization should be enforced server-side against the exact transaction data. OWASP explicitly recommends server-side transaction authorization, state-transition control, and unique authorization credentials for sensitive operations.

## CI evidence

Verified green backend test run:

- Run: 35844555465
- Commit: ea6d94eea2e105c86ec3c174db5d9c01b4e35227
- Job: pytest
- Result: success

Earlier green verification also includes run 35844542803 on commit a405e195b730d1934f225fb7de7838efe6f52a2b.

## Remaining production hardening

These items are intentionally not marked as completed by Phase C:

- enterprise/user identity provider and MFA;
- shared transactional database for multi-replica deployment;
- rate limiting and resource quotas;
- authenticated media proxy/object storage;
- full ComfyUI integration and progress/cancellation semantics;
- retention/deletion policy and operational monitoring.

Do not remove the fail-closed approval boundary while implementing those later capabilities.
