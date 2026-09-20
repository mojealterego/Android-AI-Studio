# Consent Enforcement — Threat Model and Integration Gates

## Status

This document records the security boundary required before consent can be treated as enforced in the backend. It complements `AGENT-CONSENT-PATTERNS.md`, `CONSENT-API-INTEGRATION-GATE.md`, and the consent primitives.

## Assets and trust boundaries

- **Principal identity:** the human or service principal on whose authority an action is requested.
- **Preview contract:** exact workflow ID and validated parameters shown to the approver.
- **Grant:** persisted, revocable authority scoped to principal, resource, access level, duration, and optional session/task.
- **Execution side effect:** dispatch to ComfyUI or another external system.
- **Receipt:** durable evidence of the decision and execution outcome.

The model/agent, request body, workflow parameters, tool output, and external content are untrusted. A shared bearer API key authenticates possession of that key; it does not establish a distinct human identity or prove that the human reviewed a preview.

## Required invariants

1. **Fail closed:** absent, malformed, expired, revoked, mismatched, or consumed consent blocks dispatch.
2. **Exact preview binding:** the parameters hashed for consent are the same validated parameters used to build the dispatched workflow. Do not hash one representation and execute another.
3. **Trusted principal:** never accept `subject_id` from an arbitrary request field or agent-generated content as proof of identity.
4. **Approval is a separate authority:** ordinary workflow execution credentials must not silently confer the ability to approve arbitrary grants. Approval requires a separately authenticated and auditable human action.
5. **Recheck at the boundary:** authorize immediately before dispatch; no mutation of workflow ID or parameters after authorization.
6. **No bypass route:** every route capable of triggering the same external side effect must enforce the same gate or be disabled.
7. **Revocation:** revoke is durable and checked on every use; cached grant decisions must not survive revocation.
8. **Receipts:** record principal, grant ID, action digest, decision, timestamp, and outcome. Never log credentials or unnecessary sensitive parameters.
9. **Failure semantics:** local grant consumption and remote dispatch cannot be one atomic transaction. Document whether a failed dispatch burns a one-time grant; use an idempotency key and explicit retry policy rather than silently replaying authority.
10. **Least privilege:** use exact resource scope and the smallest access level; default to once/session/task, not indefinite authority.

## Threats and mitigations

| Threat | Required mitigation |
|---|---|
| Caller forges another principal | Principal resolved from trusted auth/session; ignore client-supplied identity claims unless cryptographically verified. |
| Parameter substitution after approval | Canonical digest of validated payload; execute exactly that payload; any change requires a new preview and grant. |
| Legacy endpoint bypasses gate | Inventory all dispatch routes; enforce a shared authorization dependency or return a deprecation/disabled response. |
| Agent self-approves | Separate approval capability and authenticated human interaction; never let model output create a grant. |
| Replay of one-time grant | Atomic consume in persistence; bind to session and task; test concurrent attempts. |
| Revocation race | Check grant at dispatch boundary; define locking/transaction semantics and minimize time between check and side effect. |
| Untrusted prompt injection | Surface source and quoted instruction in the preview; server-side allowlist and policy checks remain mandatory. |
| Remote dispatch fails after consume | Durable receipt with failed/unknown outcome; explicit retry/idempotency strategy; do not silently reauthorize or replay. |
| Sensitive data leaks through logs | Allowlist receipt fields; redact tokens, secrets, and payload fields not needed for audit. |

## Integration gates before claiming end-to-end enforcement

- [ ] Establish an authenticated principal model; document whether deployment is single-user or multi-user.
- [ ] Implement a human approval path that displays concrete recipient/action/resource/parameters and records the approver.
- [ ] Make grant creation unavailable to ordinary agent execution requests.
- [ ] Validate workflow input once; use the same immutable validated object for digest, preview, and dispatch.
- [ ] Gate every route that can trigger external execution, including legacy routes.
- [ ] Add durable action receipts for denied, approved, dispatched, failed, and indeterminate outcomes.
- [ ] Define one-time grant semantics when remote dispatch fails or times out.
- [ ] Add tests for identity spoofing, exact-parameter mismatch, revoked/expired grants, concurrent replay, route bypass, and failure receipts.
- [ ] Run the backend test suite and report the actual result before marking the integration complete.

## Current limitation

The consent primitives and dispatch guard are building blocks, not proof of complete enforcement. Until trusted identity, human approval, route coverage, and receipts are integrated and tested, describe the system as **consent infrastructure in progress**, not as end-to-end consent enforcement.
