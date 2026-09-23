# AI Studio backend

FastAPI adapter between the Android client and a private ComfyUI instance.

## Security boundary

The backend is fail-closed:

- Normal API requests require Authorization: Bearer API_KEY.
- Generation approval and dispatch additionally require X-Approval-Token.
- The approval credential is separate from the service API key and is mapped server-side to an approver subject.
- Clients and agents cannot choose subject_id or actor_id.
- Workflow graphs are server-owned; arbitrary legacy graph dispatch is disabled with HTTP 410.
- Consent is bound to the exact workflow ID and canonical parameters through a SHA-256 resource digest.
- ONCE grants are consumed atomically immediately before the external ComfyUI side effect.
- Revocation, expiry, task/session scope and wrong-resource/wrong-subject checks fail closed.
- Lifecycle receipts are append-only and actor-scoped.
- Transport ambiguity is recorded as UNKNOWN and the consumed grant is not replayed automatically.

The local approval-token mapping is a deployment authenticator, not a full enterprise identity provider. It does not provide identity proofing, MFA, federation or non-repudiation. A production multi-user deployment should replace it with a stronger identity/transaction-authorization system.

## Run locally

```
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# Set COMFYUI_BASE_URL, API_KEY and a separate APPROVAL_TOKENS_JSON secret mapping.
set -a; source .env; set +a
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## API

- GET /api/v2/workflows — server-registered workflow definitions.
- POST /api/v2/approval-preview — validates and returns the exact server-derived transaction preview.
- POST /api/v2/approvals — creates a scoped consent grant after separate approval authentication.
- POST /api/v2/jobs — revalidates consent and dispatches only the approved server-owned workflow.
- POST /api/jobs — disabled legacy arbitrary-graph route; returns 410.
- GET /api/jobs and GET /api/jobs/{id} — actor-scoped durable job lifecycle.
- GET /api/jobs/{id}/result — actor-scoped completed result metadata.
- GET /api/v2/receipts — actor-scoped append-only lifecycle receipts.
- GET /api/health — liveness/health.
- GET /api/ready — readiness; requires API key, ComfyUI and persistent stores.

## Persistent stores

- Consent grants: CONSENT_DB_PATH, default ./data/consent.sqlite3.
- Jobs: JOB_DB_PATH, default ./data/jobs.sqlite3.
- Receipts: RECEIPT_DB_PATH, default ./data/receipts.sqlite3.

SQLite is appropriate for a single backend instance with persistent storage. Multi-replica deployment requires a shared transactional database and coordinated queueing.

## Production limitations

- Replace the local approval token mapping with an external identity provider or strong transaction-authorization mechanism.
- Add rate limits, request-size limits, retention/deletion controls and monitoring.
- Keep ComfyUI private; never place API secrets in the Android APK.
- Add an authenticated media proxy/object-storage layer before exposing generated files.
- Add robust ComfyUI progress events and cancellation semantics.
- Add full end-to-end integration tests against a controlled ComfyUI instance.
- Enforce model/workflow licensing and adult/consent safeguards appropriate to deployed content.

## Tests

CI runs the backend pytest suite from the repository root. The consent test matrix covers exact resource binding, wrong actor/resource, expiry/revocation, one-time consumption, tampering, ambiguous external outcomes, approval-token authentication and append-only receipt transitions.
