# Job Ownership and Result Isolation — Security Gap

## Status

**Dispatch remains disabled.** This document records a separate authorization gap in the currently implemented read APIs; it does not enable dispatch or claim that the gap has been fixed.

## Observed implementation

In `backend/app/main.py`:

- `authorize()` validates one configured shared Bearer `API_KEY`.
- `GET /api/jobs` returns the complete in-memory `jobs` collection.
- `GET /api/jobs/{job_id}` looks up a job by its UUID and queries ComfyUI history.
- `GET /api/jobs/{job_id}/result` delegates to `get_job()` and returns its outputs after completion.
- `store_job()` records a `client_id`, but the read handlers do not compare it with an authenticated principal.
- The job index is process-local memory and is explicitly described as unsuitable for multi-user production.

## Security consequence

A shared API key authenticates possession of the key, not an individual user. Consequently, the current API does not establish per-user ownership for job listing, status, or result retrieval. UUIDs are identifiers, not authorization controls. Any deployment that shares the key among users must not treat the current read endpoints as tenant-isolated.

## Required remediation before multi-user use

1. Establish a trusted authenticated principal from a real identity mechanism; do not accept the owner identity from request payloads or query parameters.
2. Persist an immutable owner/tenant identifier with each job at creation.
3. Scope list queries to the authenticated owner/tenant.
4. Enforce owner/tenant checks before querying ComfyUI history and before returning status or outputs. Prefer a consistent not-found response for inaccessible identifiers.
5. Keep job metadata and ownership in a shared durable store for multi-worker deployments; define retention and deletion behavior.
6. Add tests for cross-user list, status, and result access, including guessed UUIDs and missing/invalid identity.
7. Keep dispatch fail-closed until the separate task-scoped consent and human-approval controls are integrated atomically with job creation.

## Acceptance criteria

- User A cannot list, inspect, or retrieve outputs for User B's jobs.
- A request cannot select or override its trusted owner identity.
- Authorization is checked before any upstream ComfyUI history lookup.
- Tests cover both allowed same-owner access and denied cross-owner access for all three read routes.
- Multi-worker behavior uses shared persistence and has concurrency tests.

## Verification limits

This is a source-level finding based on the current `main.py` implementation. No runtime tests, deployment checks, or live ComfyUI requests were performed as part of this document.