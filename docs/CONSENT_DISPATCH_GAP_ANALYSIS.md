# Consent-aware dispatch: integration gap analysis

**Status:** engineering audit; dispatch remains intentionally disabled.

## Verified implementation

- `backend/app/consent_core.py` defines immutable `Grant` values and checks subject, exact resource, access, expiry/revocation, and session/task scope. `ONCE` grants require both session and task IDs.
- `backend/app/consent_binding.py` creates a canonical SHA-256 resource key from `workflow_id` and JSON parameters (sorted keys, compact separators, finite JSON only).
- `backend/app/consent_store.py` persists grants in SQLite and uses `BEGIN IMMEDIATE` to authorize and atomically consume one-time grants.
- `backend/app/consent_dispatch.py` binds authorization to the resource derived from workflow ID and parameters, then consumes the grant.
- `backend/app/consent_receipt.py` defines structured lifecycle receipt serialization; it does not provide authentication or persistence itself.
- `backend/app/consent_audit_store.py` appends immutable receipt snapshots to SQLite; its module contract explicitly leaves authenticated actor attribution to the service.
- `backend/app/main.py` currently uses a shared bearer `API_KEY`; `POST /api/v2/jobs` returns `503 CONSENT_ENFORCEMENT_NOT_CONFIGURED`. Legacy arbitrary graph dispatch returns `410 LEGACY_DISPATCH_DISABLED`.
- `backend/tests/test_dispatch_fail_closed.py` asserts both fail-closed responses.

## Integration blockers

1. **No end-user principal:** a shared API key authenticates possession of a secret, not a distinct human identity. Never accept `actor_id` or `subject_id` as authoritative request fields.
2. **No approval lifecycle endpoint:** current `main.py` exposes no endpoint to create an approval from a user-reviewed immutable request, nor to issue a scoped grant after explicit approval.
3. **No immutable server-side task snapshot:** the exact validated workflow graph/parameters used to derive the consent resource must be the same immutable values dispatched to ComfyUI.
4. **Job ownership:** current in-memory jobs do not visibly enforce per-principal ownership on list/read/result routes.
5. **Durability and concurrency:** consent storage is SQLite-backed; its own contract recommends a shared transactional database for multi-worker deployments. Jobs themselves are in-memory.
6. **Audit integration:** receipt events must be emitted from server-observed transitions, with actor identity derived from authenticated context; client-supplied lifecycle status is not authoritative.
7. **Result delivery:** ComfyUI output metadata should not be exposed as public media URLs without an authenticated media proxy or equivalent access control.

## Required safe sequence before enabling dispatch

1. Introduce real per-user authentication (or a trusted gateway that injects a verified principal); retain API-key use only as service authentication if needed.
2. Validate the workflow ID against the server-owned registry and validate/normalize parameters.
3. Create a server-side pending task snapshot and compute its canonical consent resource from those exact normalized values.
4. Present the complete action summary to the user and require an explicit approval interaction. The approval endpoint must bind the approval to the authenticated principal, pending task, resource digest, and a short expiry.
5. Issue a one-time grant bound to the principal, exact resource, session, and task. Do not use a client boolean such as `consent=true` as proof of approval.
6. Immediately before dispatch, retrieve the immutable snapshot, verify its digest, atomically consume the grant, and submit that exact graph to ComfyUI.
7. Record server-observed requested/authorized/dispatched/succeeded/failed/unknown transitions in the append-only audit store. Treat network ambiguity after submission as `UNKNOWN`, not as a definite failure or success.
8. Enforce per-principal job access, rate/request limits, retention controls, and authenticated result delivery.

## Current disposition

Do not enable `/api/v2/jobs` dispatch until the blockers above are resolved and covered by tests. The existing `503` behavior is a deliberate security boundary, not a defect to remove blindly.

## Validation status

This document records source inspection only. No backend test suite, Android build, or live ComfyUI integration test was executed as part of this audit.