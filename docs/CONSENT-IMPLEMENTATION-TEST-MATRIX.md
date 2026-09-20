# Consent implementation test matrix

Status: acceptance criteria for implementation; not evidence that tests have run.

## 1. Authorization invariants

| ID | Scenario | Expected result |
|---|---|---|
| AUTH-01 | No grant ID supplied | Reject before any external side effect |
| AUTH-02 | Unknown grant | Reject; no fallback to implicit trust |
| AUTH-03 | Subject differs from grant subject | Reject |
| AUTH-04 | Resource differs by workflow ID | Reject |
| AUTH-05 | One parameter differs from approved preview | Reject |
| AUTH-06 | Access level is read/delete rather than write | Reject dispatch |
| AUTH-07 | Grant expired or revoked | Reject |
| AUTH-08 | Session/task differs from scoped grant | Reject |
| AUTH-09 | Once grant replayed concurrently | At most one caller succeeds |
| AUTH-10 | Malformed/non-finite/non-JSON parameters | Reject before digest/dispatch |

## 2. Approval provenance and identity

- Never derive the principal from a client-controlled `subject_id`, query parameter, or request body.
- A shared service API key identifies possession of that key, not a human. Do not use it as proof of per-user approval.
- Approval creation must be behind an authenticated owner/admin boundary, and its audit record must identify the authenticated actor and authentication method.
- If deployment has only a shared bearer key, document it as single-trust-domain operation; do not claim individual user consent.
- Agent-produced text and untrusted page/tool content are not approval signals.

## 3. Preview-to-execution contract

1. Validate and normalize the user-supplied parameters.
2. Construct a deterministic preview from the normalized values.
3. Bind approval to workflow ID, exact normalized parameters, and relevant destination/configuration.
4. On execution, revalidate and recompute the binding from the exact values passed to the executor.
5. Reject any mutation between preview and dispatch; require a new preview and approval.
6. Invoke authorization immediately before the external side effect.

A hash is an integrity binding, not authentication, authorization, or proof that a human saw the preview.

## 4. External dispatch and receipts

- SQLite grant consumption and an external HTTP dispatch cannot form one atomic transaction.
- Record a durable `dispatch_started` event after authorization and before the network call.
- Record `dispatch_succeeded` only after a positive upstream response.
- Record `dispatch_failed` for a definitive failure; record `dispatch_unknown` for timeout/connection loss where the upstream may have accepted the request.
- Do not automatically replay a one-time authorization after an ambiguous outcome. Reconcile using an upstream idempotency key/status query when supported; otherwise require an explicit user decision.
- Receipts must include action ID, principal, grant ID, resource digest, timestamp, destination, outcome, and correlation ID. Do not log secrets or raw credentials.

## 5. API route coverage

Inventory every route that can trigger a side effect, including legacy routes and background workers. Each must either:

- call the same fail-closed authorization boundary; or
- be disabled/return a clear retirement response.

A route is not protected merely because another route uses the consent guard. Include direct, legacy, retry, scheduled, and worker dispatch paths in integration tests.

## 6. Consent UX acceptance criteria

- Preview shows exact consequential parameters and destination, not a lossy summary.
- Buttons name the action (for example, “Run this workflow” / “Cancel”).
- “Once” is the default; longer-lived authority is not preselected.
- Denial leaves the task safe and does not trigger repeated prompts.
- High-consequence actions enumerate realistic consequences and require typed confirmation; do not use hold-only confirmation.
- Standing grants show scope, access level, status, expiry/last-used, and immediate pause/revoke.
- Untrusted instruction provenance is displayed and cannot itself authorize an action.
- No secret is exposed to the agent; credential entry is handed off to a trusted UI/provider.
- Keyboard and screen-reader users can reach the least-destructive choice first; state/count changes are announced.

## 7. Required automated test layers

### Unit
- Digest canonicalization and rejection of invalid JSON values.
- Grant matching for subject, resource, access, duration, expiry, and revocation.
- Atomic once-grant consumption under concurrency.
- Receipt state transitions and redaction.

### API integration
- Missing/invalid consent rejects before mock upstream receives a request.
- Exact approved request succeeds once.
- Changed parameter, workflow, destination, principal, or grant rejects.
- Legacy and retry routes cannot bypass authorization.
- Revocation takes effect for the next dispatch.
- Upstream timeout yields `unknown`, not falsely reported success.

### Security regression
- Client-supplied identity cannot impersonate another principal.
- Prompt-injection text cannot create or expand a grant.
- API key alone cannot silently mint a human approval unless explicitly designated and documented as the trusted approval authority.
- Logs and receipts contain no API keys, tokens, or credentials.

## 8. Release gate

Do not label consent enforcement production-ready until route inventory is complete, identity and approval provenance are implemented, tests above run in CI, and the operator has verified revocation and failure handling in the deployed configuration.