# Backend API contract (v2)

The Android app talks only to the authenticated FastAPI backend. ComfyUI remains private.

## Authentication

Normal API access uses Authorization: Bearer <API_KEY>.

Generation dispatch additionally requires the separate X-Approval-Token credential. The server maps that credential to an approver subject; the request body never supplies a subject ID. Keep the approval credential separate from the service API key.

For a production multi-user deployment, replace the local approval-token mapping with an external identity provider or equivalent strong authenticator. The current mapping is intentionally a single-backend-instance deployment boundary.

## Approval flow

### POST /api/v2/approval-preview

Authenticated with the normal API key. The server validates the server-owned workflow and returns the exact workflow version, parameters, resource digest, task/session scope and consequence description. This endpoint has no external side effect.

### POST /api/v2/approvals

Authenticated with both the API key and X-Approval-Token.

The client sends the preview digest plus the exact same workflow ID, parameters, task/session and requested duration. The server recomputes the digest and rejects any mismatch. A scoped grant is then persisted.

Example:

{
  "workflow_id": "configured-image-workflow",
  "parameters": {"width": 1024, "height": 1024, "steps": 24},
  "session_id": "session-1",
  "task_id": "task-1",
  "preview_digest": "<64-hex-sha256>",
  "duration": "once",
  "expires_in_seconds": 300
}

### POST /api/v2/jobs

Authenticated with both the API key and X-Approval-Token. The server derives the actor from the approval credential, rebuilds the server-owned workflow, revalidates the exact consent binding immediately before the ComfyUI call, and atomically consumes an ONCE grant.

The legacy arbitrary-graph POST /api/jobs endpoint remains disabled with HTTP 410 and cannot bypass the consent boundary.

## Jobs

- GET /api/jobs — authenticated jobs, actor-scoped when an approval token is supplied.
- GET /api/jobs/{id} — ownership checked before any ComfyUI request.
- GET /api/jobs/{id}/result — ownership checked before any upstream request.
- GET /api/v2/receipts — actor-scoped immutable lifecycle receipts; optional task_id filter.

## Consent and failure semantics

- Missing, expired, revoked, wrong-actor, wrong-resource and already-consumed grants fail closed.
- Workflow parameters are canonically hashed; changing a parameter changes the resource digest.
- Approval credentials are separate from the normal API service credential.
- Receipt transitions are append-only and validated server-side.
- A transport timeout/connection failure after dispatch is recorded as unknown; the one-time grant must not be blindly replayed.
- A ComfyUI HTTP rejection is recorded as failed.
- Credentials, bearer tokens and approval secrets are never written to receipts.

## Production boundary

The local APPROVAL_TOKENS_JSON mapping is a deployment-level approval authenticator, not a full enterprise identity provider. It establishes a trusted server-derived approver subject but does not provide identity proofing, MFA, federation, or non-repudiation. Those controls belong in the next production identity layer.

### Media delivery

Completed job outputs are exposed only through an authenticated backend media proxy:

- `GET /api/jobs/{job_id}/media/{index}` requires the normal API credential and enforces job ownership before contacting ComfyUI.
- The client addresses media by a server-assigned output index; it never supplies an arbitrary ComfyUI filename as an upstream request.
- The backend validates the stored filename, subfolder and media type, streams bytes from private ComfyUI `/view`, and does not expose the ComfyUI host to Android.
- Media responses are private/no-store and include `X-Content-Type-Options: nosniff`.
- `MEDIA_MAX_BYTES` limits the maximum proxied object size (default 250 MiB).
- Android downloads through the authenticated proxy and opens the resulting file through an app-cache-only `FileProvider`.

The proxy is intentionally not a public object-storage URL or bearerless download link. A future multi-user deployment should bind media access to the same external identity/tenant model used for job ownership.


### Job cancellation and lifecycle (Phase F)

- POST /api/jobs/{job_id}/cancel is authenticated and ownership-scoped.
- Pending jobs are removed with ComfyUI POST /queue using the exact prompt_id.
- Running jobs are interrupted with a prompt-scoped POST /interrupt body; the backend never sends a global/unscoped interrupt.
- Cancellation is idempotent for terminal jobs and is persisted as CANCELLED.
- GET /api/jobs/{job_id} reconciles ComfyUI history and queue state. An execution_interrupted history message is reported as CANCELLED, not as a generic failure.
- Queue position is exposed as queue_position while a job is queued. The existing percentage field is not treated as a fake estimate; precise per-node sampling progress remains a future WebSocket integration.
- Android can open results, show sampled in-app image previews, save images/videos into Android MediaStore on Android 10+, and removes stale private cache files older than 24 hours.
