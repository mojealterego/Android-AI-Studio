# Consent Runtime Release Gate

Status: **BLOCKED — runtime enforcement and integration tests are not yet verified.**

This gate is normative for releasing Agent Consent Patterns in Android-AI-Studio. The presence of consent modules and design documents is not proof that external side effects are protected.

## Current observed risk

`backend/app/main.py` exposes `POST /api/v2/jobs`, which builds a server-owned workflow and dispatches it to ComfyUI, and legacy `POST /api/jobs`, which accepts a client-supplied workflow graph and dispatches it. Both currently rely on the shared API bearer key. That key does not identify the human who approved a specific action, and neither route currently calls the consent dispatch guard.

## Required release blockers

- [ ] Establish a trustworthy authenticated principal for each caller. Never accept `actor_id` or `subject_id` from an untrusted request body as proof of identity.
- [ ] Implement a human approval flow that presents the exact workflow ID, normalized parameters, action, duration/scope, and consequences before creating a grant.
- [ ] On every side-effect route, derive the workflow resource server-side from the exact validated parameters and bind it to the approved preview. Reject mismatches.
- [ ] Route both v2 and legacy dispatch through the same enforcement boundary. Until legacy can be safely migrated, disable it or reject it; do not leave a bypass.
- [ ] Enforce grant subject, resource, access, expiry, revocation, task/session scope, and one-time consumption immediately before dispatch.
- [ ] Make job creation idempotent. Do not blindly retry an external dispatch after an ambiguous timeout; record `unknown` and reconcile with ComfyUI first.
- [ ] Persist lifecycle receipts as append-only events. Enforce legal state transitions and duplicate-event handling in the service layer; the SQLite journal alone does not provide these guarantees.
- [ ] Authenticate receipt reads and enforce actor/task-level authorization. A receipt ID is not a secret or authorization token.
- [ ] Exclude API keys, bearer tokens, secrets, and unnecessary personal data from receipts and logs.
- [ ] Add integration tests proving each route is blocked without valid consent and that malformed, expired, revoked, mismatched, replayed, or wrong-actor grants cannot dispatch.
- [ ] Run the complete backend test suite and record the exact command, environment, result, and commit SHA.

## Dispatch failure semantics

A one-time grant consumption and a remote ComfyUI HTTP request cannot be made atomic with the current architecture. A timeout or connection loss after sending the request is ambiguous. The service must persist an `unknown` outcome, reconcile against ComfyUI using a stable idempotency/correlation key, and avoid blind replay. If reconciliation is impossible, surface the uncertainty rather than claiming failure or success.

## Required evidence for release

1. Code review confirms every side-effect route passes through one shared guard.
2. Automated integration tests exercise both current and legacy routes.
3. CI is green for the exact release commit.
4. An audit trail demonstrates requested → authorized/denied → dispatched → succeeded/failed/unknown transitions without overwriting prior events.
5. Security review confirms principal derivation, actor-scoped receipt access, revocation behavior, and safe handling of ambiguous dispatches.

## Decision

Do not label the consent system production-ready until every blocker above has evidence attached to the release commit. Documentation-only progress must be reported as documentation-only progress.