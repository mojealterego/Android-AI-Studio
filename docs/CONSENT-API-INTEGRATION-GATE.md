# Consent API Integration Gate

## Status

Design gate for integrating `backend/app/consent_core.py` and `backend/app/consent_store.py` into FastAPI job execution. This document is not evidence that runtime enforcement exists.

## Current constraints observed in `backend/app/main.py`

- Authentication is one shared `API_KEY` bearer credential.
- `/api/v2/jobs` builds a graph from a server-owned workflow registry.
- Legacy `/api/jobs` accepts a caller-supplied workflow graph.
- Jobs are held in a process-local dictionary.
- There is no per-principal identity or job ownership boundary in the current code.

## Blocking security requirements

1. **Principal identity:** replace shared-key-only authorization with authenticated, stable principal IDs before claiming multi-user consent isolation. Never derive a principal from request body fields or agent-provided text.
2. **Separate decision authority:** agent/job requests may create a pending consent request, but must not approve it. Approval must come from an authenticated human-controlled client context.
3. **Immutable action preview:** persist a canonical action payload and cryptographic digest at preview time. Approval binds to that exact digest. Any change to workflow ID, validated parameters, resource, recipient, amount, or other consequential field invalidates approval and requires a new preview.
4. **Fail closed:** no grant, rejected request, expired/revoked grant, mismatched principal/resource/scope, or failed limit check means no side effect.
5. **Enforcement point:** check authorization immediately before dispatch to ComfyUI. Avoid a time gap in which parameters can change after approval. For one-time grants, atomically consume at the enforcement boundary; document the unavoidable distributed-side-effect/retry semantics.
6. **No client workflow bypass:** migrate clients to `/api/v2/jobs`; disable or strictly isolate `/api/jobs` before asserting that consent gates all job creation.
7. **Revocation:** revocation must prevent subsequent dispatches. Define behavior for already-dispatched jobs separately; do not imply that revocation can undo a ComfyUI job already accepted.
8. **Receipts:** record principal, action digest, workflow ID, resource/scope, grant ID, decision timestamp, dispatch outcome, and any honest recovery path. Do not log bearer tokens or secrets.
9. **Persistence and deployment:** SQLite is suitable only for a single-process/single-host deployment with a persistent volume and reviewed concurrency behavior. Multi-worker deployment requires shared transactional storage and migration/backup strategy.
10. **Limits:** enforce spend, queue, and action-count ceilings outside the agent; reject at the limit (fail closed).

## Suggested API lifecycle (not yet implemented)

- `POST /api/v2/consent-requests`: create a pending request containing principal, exact resource, access level, duration, reason, provenance, and immutable action digest. Does not grant authority.
- `GET /api/v2/consent-requests/{id}`: return the exact preview to an authorized principal.
- `POST /api/v2/consent-requests/{id}/decision`: authenticated human decision (`approve` or `deny`), explicit duration, and validated scope. Denial is safe and final for that request.
- `GET /api/v2/consents`: list active grants with scope, duration, expiry, and last-used information.
- `POST /api/v2/consents/{grant_id}/revoke`: immediate revocation for subsequent protected actions.
- `POST /api/v2/jobs`: require a valid approved request/grant bound to the exact action digest; revalidate the registry-built workflow and parameters before dispatch.
- `GET /api/v2/actions/{id}/receipt`: retrieve the resulting action receipt for the owning principal.

## Required test matrix

- No grant, denial, expiry, revocation, wrong principal, wrong resource, wrong access, wrong session/task: dispatch count remains zero.
- Modify any previewed parameter after approval: reject and require a new preview.
- Reuse a one-time grant concurrently: at most one request passes the atomic grant-consumption boundary.
- Attempt to approve as an agent/API job caller: reject.
- Attempt legacy arbitrary-graph dispatch: reject or remain behind an explicitly documented migration-only boundary.
- Revoke before dispatch: reject; revoke after dispatch: receipt must accurately state that execution had already begun.
- Limit reached or limit service unavailable: reject, never borrow capacity.
- Receipt created for success and failure without exposing credentials.

## Release gate

Do not describe consent enforcement as integrated until the FastAPI routes, authenticated principal model, dispatch guard, legacy-route policy, persistence configuration, and automated tests are implemented and executed. A successful Git commit alone is not runtime verification.
