# Consent Receipt Lifecycle and Dispatch Invariants

## Purpose

Define the server-side lifecycle for consequential actions so receipts describe observed events rather than optimistic agent claims. This document is normative for future API/dispatcher integration; it does not itself enforce runtime behavior.

## State transitions

Allowed progression:

- `requested` → `authorized`, `denied`, or `cancelled`
- `authorized` → `dispatched`, `failed`, or `cancelled`
- `dispatched` → `succeeded`, `failed`, or `unknown`
- `unknown` → `succeeded` or `failed` only after reconciliation against the external system

Terminal states: `succeeded`, `failed`, `denied`, `cancelled`. `unknown` is non-terminal for reconciliation but must never trigger an automatic replay of a potentially non-idempotent action.

Reject all unlisted transitions. A repeated event must be idempotently deduplicated using a stable event/idempotency key; do not overwrite prior receipt events.

## Required event semantics

1. `requested`: server has accepted and normalized a request, but has not authorized it.
2. `authorized`: authorization succeeded against server-derived principal, exact resource, exact action, and a digest of the immutable execution parameters.
3. `dispatched`: the server actually initiated the external side effect. Record the destination/system and a correlation identifier when available.
4. `succeeded`: only after a definitive success response or authoritative reconciliation.
5. `failed`: only after a definitive failure response. Keep the error code concise; never persist secrets or raw credentials.
6. `unknown`: timeout, connection loss, or ambiguous response where the external side effect may have occurred. Do not report success or blindly retry.
7. `denied` / `cancelled`: record the decision and stop before external dispatch.

## Immutable action contract

The preview and execution must share the same normalized parameters and request digest. Any mutation after approval invalidates the approval and requires a new preview and authorization. The digest should cover the workflow/action identifier and canonical parameters; exclude credentials and volatile transport metadata.

## Authorization and identity boundary

- Derive `actor_id` from authenticated server-side identity, never from a request body or caller-controlled header.
- A global API key identifies a credential, not necessarily a human principal. Do not use it as proof that a particular user approved a grant unless the deployment explicitly defines and enforces that principal model.
- Scope grants to exact action/resource and bounded duration. Revoke immediately; fail closed when the grant store or identity provider is unavailable.
- Enforce the guard on every route capable of causing a side effect, including legacy routes. A protected new route does not compensate for an unguarded legacy route.

## One-time grant and external dispatch

A database transaction cannot atomically commit with an external HTTP side effect. If an ONCE grant is consumed before dispatch and dispatch fails, the grant may be spent. If the dispatch response is ambiguous, persist `unknown`, reconcile by external correlation/idempotency key, and do not replay automatically. This limitation must be visible to the user.

## Audit store requirements

- Append-only events; no update/delete API.
- Enforce valid state transitions and idempotency in the service layer before append.
- Authenticate and authorize reads of actor-specific history; never expose another actor's receipts by guessing a receipt ID.
- Protect database access and backups; append-only application methods alone are not tamper-evidence against a database administrator.
- Retention and deletion policy must be explicit and compatible with applicable privacy requirements.
- Receipt payloads must exclude passwords, API keys, access tokens, payment credentials, and unnecessary personal data.

## Acceptance tests before production wiring

- Invalid transition is rejected without adding an event.
- Duplicate event/idempotency key does not create a second logical event.
- Actor identity cannot be overridden by body/header input.
- Preview digest mismatch blocks dispatch.
- All side-effect routes, including legacy endpoints, pass through the same guard.
- Definitive success/failure and ambiguous timeout produce distinct statuses.
- `unknown` does not automatically retry; reconciliation is explicit and audited.
- Receipt listing is actor-scoped and access-controlled.
- Revocation between preview and dispatch blocks the action.
