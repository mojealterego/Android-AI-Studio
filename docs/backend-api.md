# Backend API contract (v1)

All endpoints are served by the authenticated FastAPI backend over HTTPS. The Android app must never call ComfyUI directly in production.

## `GET /api/health`
Returns `{"status":"ok"}` when the API is ready.

## `POST /api/jobs`
Request:
```json
{
  "type": "IMAGE",
  "prompt": "A detailed scene description",
  "negativePrompt": "",
  "workflowId": "configured-image-workflow",
  "parameters": {"width":"1024","height":"1024","steps":"24"}
}
```
Response:
```json
{"id":"job-id","status":"queued","progress":0.0,"resultUrl":null,"error":null}
```

## `GET /api/jobs/{id}`
Returns the same job shape with `queued`, `running`, `succeeded`, `failed`, or `cancelled` status. `progress` is 0.0–1.0.

## `GET /api/jobs`
Returns the authenticated user's jobs, newest first.

## `GET /api/jobs/{id}/result`
Returns a short-lived authorized URL or a stream for the completed output.

## `POST /api/jobs/{id}/cancel`
Requests cancellation where supported by the active workflow.

## Security requirements
- Authenticate every non-health request.
- Authorize job ownership on every read, cancel, and result request.
- Validate prompt length, parameter ranges, workflow IDs, MIME types, and output size.
- Rate-limit generation and enforce per-user GPU quotas.
- Keep ComfyUI private; never ship API keys or internal hostnames in the APK.
- Treat prompts and generated media as private user data; define retention and deletion behavior.
