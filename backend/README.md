# AI Studio backend (prototype)

FastAPI adapter between the Android client and a private ComfyUI instance. The current API is intentionally fail-closed for job dispatch: this repository does **not** yet provide the authenticated human-consent approval flow required to authorize external generation.

## Run locally

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# Edit COMFYUI_BASE_URL and set a long random API_KEY
set -a; source .env; set +a
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

OpenAPI docs: `http://localhost:8000/docs`.

## API status

All protected endpoints require `Authorization: Bearer <API_KEY>`.

- `GET /api/v2/workflows` — lists server-registered workflow definitions.
- `POST /api/v2/jobs` — currently returns `503 CONSENT_ENFORCEMENT_NOT_CONFIGURED`; it does not dispatch to ComfyUI.
- `POST /api/jobs` — legacy arbitrary-workflow dispatch is disabled and returns `410 LEGACY_DISPATCH_DISABLED`.
- `GET /api/jobs` and `GET /api/jobs/{id}` — prototype job-status routes; job state is held in process memory and is not durable.
- `GET /api/jobs/{id}/result` — result route; only available for a completed job.
- `GET /api/health` — checks backend/ComfyUI health; it is not a readiness guarantee for generation.

## Consent and dispatch boundary

The consent modules provide parameter-bound resource identifiers, grant validation/consumption, SQLite persistence, and append-only receipt storage. They do not, by themselves, authenticate a unique human principal or implement an approval UI/API. The shared `API_KEY` is a service credential, not per-user identity.

Do not enable job dispatch by trusting a `subject_id`, grant ID, workflow graph, or approval claim supplied by an agent/client. Before dispatch can be enabled, the server needs a separately authenticated human approval flow, trusted principal attribution, server-side workflow allowlisting and validation, immutable parameter snapshotting, exact consent binding, atomic one-shot consumption immediately before the external side effect, and audit/error handling. Keep dispatch fail-closed until those pieces are integrated and tested.

## Production limitations

- Jobs are held in process memory and disappear on restart; use durable storage and a queue suitable for the deployment.
- Add user accounts and per-user ownership checks; the shared API key does not provide tenant isolation.
- Add rate limits, request-size limits, retention/deletion controls, and operational monitoring.
- Keep ComfyUI private; expose only the authenticated backend. Do not put API secrets in the Android APK.
- Add an authenticated media proxy/object-storage layer before exposing generated files to clients.
- Add cancellation, progress events, robust ComfyUI error parsing, and integration tests.
- Only use models and workflows whose licenses permit the intended use; enforce adult-only and consent safeguards.

## Tests

Run the backend test suite from `backend/` using the repository's configured test dependencies. Confirm the dependency file and CI configuration before relying on a particular local test command. No test or ComfyUI integration result is implied by this documentation update.
