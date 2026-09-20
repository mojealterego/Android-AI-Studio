# Job Ownership — Implementation Contract

## Status

Planning artifact only. This document does not implement authentication, persistence, or enable dispatch. The current shared `API_KEY` is not a per-user identity provider.

## Required trust boundary

The API must derive `principal_id` exclusively from a validated authentication credential. It must never derive ownership from `client_id`, `job_id`, request JSON, query parameters, or an unverified JWT claim.

Before coding, select and configure one identity model:

- **OIDC access tokens (recommended for multi-user deployments):** validate signature against trusted issuer JWKS, issuer, audience, expiry, not-before, and required subject. Map `(issuer, subject)` to an internal immutable principal ID.
- **Single-user deployment:** explicitly document that the service is single-tenant and restrict network access; do not represent this as multi-user isolation.

Do not implement a homemade JWT verifier or treat a static shared API key as an end-user principal.

## Data model contract

Each job record must persist at minimum:

- `id`: server-generated opaque job identifier;
- `owner_id`: immutable internal principal/tenant identifier;
- `prompt_id`: upstream ComfyUI identifier;
- `type`, `status`, `progress`, timestamps, and output metadata;
- `workflow_id` and canonical parameter digest where applicable.

The owner field is write-once and populated from the validated principal at creation. Client-supplied owner fields must be rejected (`extra="forbid"` on request models).

## Read authorization contract

For `GET /api/jobs`, query only rows belonging to the authenticated principal.

For `GET /api/jobs/{job_id}` and `GET /api/jobs/{job_id}/result`:

1. Validate the principal.
2. Fetch the job constrained by both `id` and `owner_id` in the storage query.
3. If no owned row exists, return the same `404 Job not found` response for absent and foreign-owned IDs.
4. Only after ownership succeeds may the handler contact ComfyUI or return status/output data.

Never fetch by ID first and perform an optional later check. Avoid leaking whether a foreign job exists.

## Persistence and concurrency

The current process-local dictionary is not suitable for multi-worker or restart-safe ownership. Use a shared durable database (PostgreSQL recommended for multi-worker production) with an index on `(owner_id, id)`. Define transaction boundaries, retention, deletion, and output-reference cleanup. Do not use SQLite as a distributed multi-worker coordination mechanism without an explicit deployment constraint and concurrency design.

## Dispatch and consent

Job creation remains fail-closed until authenticated principal, immutable validated workflow parameters, task-scoped consent, atomic one-shot consumption, durable job creation, and audit event semantics are designed and tested together. Do not consume consent and then create a job in a way that can lose authorization or duplicate dispatch; define an idempotency/recovery strategy.

## Required tests

- Owner A lists only A's jobs; owner B's jobs are absent.
- Same-owner status and result access succeeds.
- Cross-owner status and result access return indistinguishable 404 responses.
- A foreign or random UUID causes no ComfyUI history request.
- Missing, expired, wrong-issuer, wrong-audience, or invalid credentials are rejected.
- Request-supplied `owner_id`/tenant identity is rejected or ignored only if schema explicitly forbids it (prefer rejection).
- Persistence survives process restart; concurrent requests preserve ownership and consent invariants.

## Rollout sequence

1. Select identity provider and deployment tenancy model.
2. Add principal validation and tests without enabling dispatch.
3. Add durable job repository and owner-scoped queries.
4. Migrate existing in-memory jobs as non-owned/unavailable; never infer owners retroactively.
5. Add cross-tenant access tests and verify no upstream calls occur before authorization.
6. Integrate consent + approval + durable dispatch with idempotency and audit.
7. Enable dispatch only after all acceptance tests pass and operational configuration is present.

## Acceptance gate

No multi-user production claim until all required tests pass in CI and the deployed identity issuer, audience, database, and retention settings are verified.