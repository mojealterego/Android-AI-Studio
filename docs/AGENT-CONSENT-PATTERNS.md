# Agent Consent Patterns — Android AI Studio

Status: implementation specification; this document does not claim runtime enforcement until the backend and Android client implement and test these contracts.

## Purpose

Define a server-enforced consent and authority layer for the Android client -> FastAPI -> private ComfyUI architecture. Support ordinary creative work and adult-oriented workflows without treating an NSFW profile as blanket permission or bypassing model/provider restrictions.

## Non-negotiable invariants

1. The backend is the authority for grants; client UI state is never proof of consent.
2. A delegated agent's capabilities are the intersection of the user's granted scopes, the authenticated principal's rights, workflow capabilities, and applicable provider policy.
3. Default grant is one action. Standing grants require an explicit duration, narrow scope, visible status, last-used timestamp, pause, and revocation.
4. Consent is specific to project, operation, resource/reference, media class, and purpose. Changing any material field invalidates the prior action approval.
5. No secrets are supplied to agents. Credentials remain in platform secure storage or a human-controlled handoff.
6. Declining is safe; no hidden queued action may continue after denial or revocation.
7. Every consequential operation produces an immutable receipt; never advertise undo unless implemented.
8. Budget, request-rate, concurrency, and output-size ceilings are enforced outside the model.
9. Untrusted prompt/reference metadata is data, not authority. Injection indicators must be surfaced and cannot override system policy.
10. Adult workflows require lawful adult content and valid rights/consent for identifiable subjects. No sexualization of real people without appropriate consent, no minors or age-ambiguous subjects, and no safeguard circumvention.

## Permission vocabulary

- `workflow:read`: list server-published workflow metadata.
- `job:create`: submit one approved generation job.
- `reference:read`: use explicitly selected project references.
- `asset:edit`: modify an explicitly selected asset.
- `asset:export`: export completed outputs to a selected destination.
- `asset:publish`: publish externally; always separate from generation and individually confirmed.
- `project:delete`: destructive; require a typed confirmation and enumerate affected data.

Read scopes may be offered by default. Write, export, publish, and delete are opt-in. The agent must not receive scopes merely because a workflow supports them.

## Consent lifecycle

`DRAFT -> PREVIEWED -> APPROVED -> EXECUTING -> COMPLETED | FAILED | CANCELLED`

Any material change after preview returns the action to `DRAFT`. Revocation blocks future actions immediately and cancels queued work where cancellation is supported. In-flight work must be marked and handled according to provider cancellation capability; do not claim it was stopped if it cannot be stopped.

## Action Preview contract

Before execution show verbatim:

- project and selected input/reference identifiers;
- operation and media type (`IMAGE` or `VIDEO`);
- workflow ID and version, provider/model identity;
- exact prompt and negative prompt (where applicable), plus key generation parameters;
- content profile (`STANDARD`, `ARTISTIC`, `ADULT`);
- output destination, retention behavior, and any external recipient;
- estimated/maximum cost and applicable rate/quantity limits;
- grant scope, duration, and whether this is once/session/scoped standing authority;
- source provenance and any untrusted-instruction warning.

The approved immutable request is the request executed. Any change requires a new preview and approval.

## Consequence tiers

- Reversible (draft prompt/settings): one clear action.
- Undoable (local asset edit): one action plus a real recovery path.
- External or irreversible (publish/share/delete): individually reviewed; enumerate consequences; require typed confirmation for destructive operations. Never include these in bulk approval.

## Consent Memory and authority

Supported durations: `ONCE`, `TASK`, `SESSION`, `SCOPED_STANDING`. `ALWAYS` is not a default and should not be offered for publication, deletion, purchases, or other irreversible actions. Expire standing grants and request reconfirmation. Settings must expose scope, status, creation time, expiry, last use, pause, and revoke.

Autonomy is configured per action category: `NEVER`, `ASK`, `AUTOMATIC_WITHIN_GRANT`. Prompt text cannot change this boundary. Spend/rate caps fail closed.

## Receipt schema (logical)

- `receipt_id`, `principal_id`, `project_id`, `job_id`
- `action`, `resource_ids`, `workflow_id`, `workflow_version`
- `consent_grant_id`, `consent_version`, `approval_timestamp`
- `request_digest`, `provider`, `started_at`, `finished_at`
- `status`, `result_refs`, `failure_code`, `cancellation_capability`
- `policy_decision`, `provenance_flags`

Do not log raw secrets. Minimize or redact sensitive prompt/reference content in operational logs; define retention and deletion behavior explicitly.

## API design target

- `POST /api/v2/consent/grants`: create a narrowly scoped grant after user confirmation.
- `GET /api/v2/consent/grants`: list current principal's grants and last-used metadata.
- `POST /api/v2/consent/grants/{grant_id}/revoke`: revoke and cancel queued work where possible.
- `POST /api/v2/actions/preview`: validate requested action and return canonical preview plus digest.
- `POST /api/v2/actions/{preview_id}/approve`: bind approval to the immutable digest.
- `POST /api/v2/jobs`: accept only an approved preview/grant; never accept client-supplied authority claims as trusted.
- `GET /api/v2/receipts`: list the principal's receipts.

All endpoints require authenticated principal identity and resource-level authorization. A single shared API key is not sufficient for multi-user isolation.

## Required backend changes

1. Deprecate legacy `/api/jobs` client-supplied workflow graphs; reject or disable it in production after migration.
2. Replace process-local `jobs` storage with durable per-principal storage (PostgreSQL; queue state in a durable queue/Redis as appropriate).
3. Bind jobs and results to principal/project ownership; prevent cross-user listing and retrieval.
4. Add server-side consent grant, preview, approval, revocation, and receipt persistence.
5. Add rate/cost/concurrency quotas and fail-closed behavior.
6. Ensure workflow manifests declare supported content profile and required capabilities; server policy decides eligibility.
7. Keep ComfyUI private; never expose its endpoint or credentials in the APK.

## Android UX requirements

- Consent prompts are labeled groups/dialogs with clear title, concrete facts, and safe initial focus.
- `Deny`/`Cancel` is always available and never leaves a pending job.
- Default to `Allow once`; standing duration is explicit and not preselected.
- Show grant cards with exact scopes, status, expiry, last use, pause, and revoke.
- Distinguish generate, edit, export, publish, and delete approvals.
- Do not communicate critical state by color alone; support screen readers and scalable text.

## Acceptance tests

- Missing, expired, revoked, wrong-project, wrong-resource, wrong-purpose, or insufficient-scope grant blocks execution.
- Changing prompt, workflow version, model, reference, destination, or material parameters invalidates approval digest.
- Revocation prevents queued actions from starting; in-flight behavior is accurately reported.
- A principal cannot list, inspect, cancel, or fetch another principal's jobs/results/receipts.
- Legacy arbitrary graph endpoint is disabled or constrained in production.
- Limits stop execution at cap without borrowing or silent fallback.
- Denial creates no job and is not retried by an agent.
- Every completed/failed/cancelled consequential action has a receipt.
- Adult profile does not override age, consent, rights, or provider restrictions.
- Prompt injection in a reference or tool result cannot grant permissions or alter authority.
