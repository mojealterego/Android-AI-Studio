# AI Studio backend (prototype)

FastAPI adapter between the Android client and a private ComfyUI instance.

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

## Request

`POST /api/jobs` with `Authorization: Bearer <API_KEY>` and JSON:

```json
{
  "type": "IMAGE",
  "prompt": "A cinematic landscape",
  "negativePrompt": "",
  "workflow": {"...": "ComfyUI API-format workflow"}
}
```

The workflow must be exported in ComfyUI API format. This prototype passes it to `POST /prompt`, then checks `/history/{prompt_id}`.

## Important limitations before production

- Jobs are held in process memory and disappear on restart; use PostgreSQL plus Redis/Celery or a durable queue.
- `workflow` is currently accepted from the client. Production must use server-owned, allowlisted workflow templates and validate node types and inputs.
- Add user accounts, per-user ownership checks, rate limits, request-size limits, audit logging, retention/deletion controls, and HTTPS.
- Keep ComfyUI private; expose only the authenticated backend. Do not put API secrets in the Android APK.
- Result metadata is returned from ComfyUI; add an authenticated media proxy/object-storage layer before exposing files to clients.
- Add cancellation, progress events, robust ComfyUI error parsing, and tests.
- Only use models and workflows whose licenses permit the intended use; enforce adult-only and consent safeguards.
