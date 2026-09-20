# Consent Runtime Enforcement Plan

## Status

This document is an integration gate for the existing consent primitives. It does not claim that runtime enforcement is already active.

## Verified current boundary

`backend/app/main.py` currently protects routes with a shared bearer `API_KEY`. The key proves possession of a shared secret; it does not identify an individual human approver or prove approval of a specific action.

Both job-creation routes cause an external side effect by posting to ComfyUI:

- `POST /api/v2/jobs` builds a graph from a server-owned workflow ID and parameters.
- `POST /api/jobs` accepts a client-supplied workflow graph (legacy route).

Neither route currently invokes the consent dispatch guard. Therefore, the existence of consent core/store/dispatch modules does not currently mean job execution is consent-gated.

## Required enforcement sequence

1. **Establish an authenticated principal.** Resolve a stable actor identifier from a verified authentication mechanism. Do not derive `actor_id` from request JSON, query parameters, a caller-selected header, or the shared API key alone.
2. **Create a server-side action intent.** Bind actor, task, action type, exact workflow identity, canonical parameters, and resource scope. For v2, bind the resolved server-owned workflow specification and validated parameters. For v1, either disable the route or define an equally strict canonicalization/validation contract; do not treat an arbitrary graph as implicitly safe.
3. **Obtain explicit, scoped consent.** Approval must reference the exact intent/resource digest and access level, with a bounded lifetime. A grant for one workflow or parameter set must not authorize another.
4. **Enforce at the side-effect boundary.** Every route that can enqueue, mutate, delete, publish, spend, or otherwise cause a consequential effect must call the same fail-closed dispatcher immediately before the external action. No legacy or alternate route may bypass it.
5. **Record lifecycle receipts.** Append server-observed receipt events for requested, authorized, dispatched, and terminal outcomes. Derive actor identity from the authenticated principal. Never include credentials or raw secrets.
6. **Handle uncertain external outcomes.** If the request may have reached ComfyUI but the response is ambiguous, record `unknown`, reconcile against authoritative ComfyUI state, and do not blindly replay a one-time grant or action.
7. **Scope audit reads.** Receipt retrieval must enforce actor/tenant authorization at the API layer; an actor filter in a storage method is not authorization by itself.

## Route policy

| Route | Current concern | Required disposition |
|---|---|---|
| `POST /api/v2/jobs` | Server-owned workflow, but no consent check | Require authenticated actor + exact intent grant + dispatch guard |
| `POST /api/jobs` | Client-supplied arbitrary graph; no consent check | Disable by default or migrate through strict canonical validation and the same guard |
| Any future side-effect route | Potential bypass | Must be registered in a route/action inventory and covered by integration tests |

## Fail-closed conditions

Reject before the external call when any of these is true:

- no verified actor identity;
- no matching, unexpired, unrevoked grant;
- actor, action, resource, access level, workflow digest, or parameters mismatch;
- malformed or non-canonical intent;
- consent store or audit prerequisites are unavailable;
- route is not covered by the enforcement inventory.

Do not silently downgrade to legacy behavior when the consent subsystem is unavailable.

## Required integration tests

- Unauthenticated request cannot enqueue a job.
- Shared API key alone cannot establish human consent.
- Missing, expired, revoked, wrong-actor, wrong-action, and wrong-resource grants all fail before ComfyUI is called.
- Changing any workflow parameter invalidates the grant binding.
- Both v2 and legacy v1 routes are gated; if v1 is disabled, test that it returns a non-success response without calling ComfyUI.
- A one-time grant cannot authorize two concurrent dispatches.
- A ComfyUI timeout/ambiguous response becomes `unknown` and is not blindly replayed.
- Receipt history is append-only and API reads are actor/tenant scoped.
- Tests assert the external side-effect mock was not called on every denial path.

## Implementation order

1. Choose and implement the real principal-authentication mechanism.
2. Define intent creation and consent approval API contracts against that principal.
3. Integrate `consent_binding` and `consent_dispatch` into the v2 execution path.
4. Disable or fully gate the v1 route; do not leave a bypass.
5. Add lifecycle transition validation and receipt persistence around dispatch.
6. Add actor-scoped receipt endpoints and integration tests.
7. Run the complete backend test suite and report actual results.

## Explicit non-claims

Until the above is implemented and tested, this repository must not claim that AI Studio job execution is consent-enforced, that receipts prove successful execution, or that the shared API key identifies a human approver.