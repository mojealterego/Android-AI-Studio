from __future__ import annotations

import hmac
import os
import uuid
from typing import Literal, Any

import httpx
from fastapi import FastAPI, Header, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, ConfigDict

from .workflow_registry import registry
from .consent_dispatch import DispatchAuthorization, authorize_dispatch
from .consent_store import ConsentStore
from .consent_core import ConsentError
from .consent_binding import workflow_resource
from .job_store import JobStore

COMFYUI = os.getenv("COMFYUI_BASE_URL", "http://127.0.0.1:8188").rstrip("/")
API_KEY = os.getenv("API_KEY", "").strip()
# A single-instance principal is deliberately not a multi-user identity system.
INSTANCE_OWNER_ID = os.getenv("INSTANCE_OWNER_ID", "single-instance").strip()
MAX_PROMPT_LENGTH = int(os.getenv("MAX_PROMPT_LENGTH", "4000"))
MAX_WORKFLOW_NODES = int(os.getenv("MAX_WORKFLOW_NODES", "250"))
MAX_TRACKED_JOBS = int(os.getenv("MAX_TRACKED_JOBS", "500"))
CONSENT_DB_PATH = os.getenv("CONSENT_DB_PATH", "./data/consent.sqlite3")
CONSENT_STORE = ConsentStore(CONSENT_DB_PATH)
JOB_DB_PATH = os.getenv("JOB_DB_PATH", "./data/jobs.sqlite3")
JOB_STORE = JobStore(JOB_DB_PATH)

app = FastAPI(title="AI Studio API", version="0.2.3")
origins = [x.strip() for x in os.getenv("CORS_ORIGINS", "").split(",") if x.strip()]
app.add_middleware(CORSMiddleware, allow_origins=origins or [], allow_credentials=False,
                   allow_methods=["GET", "POST"], allow_headers=["Authorization", "Content-Type"])

# Compatibility cache for callers/tests; JOB_STORE is the durable source of truth.
JOB_STORE.recover_inflight(INSTANCE_OWNER_ID)
jobs: dict[str, dict[str, Any]] = {job["id"]: job for job in JOB_STORE.list_owned(INSTANCE_OWNER_ID, MAX_TRACKED_JOBS)}

class CreateJob(BaseModel):
    """Legacy v1 request. Dispatch is disabled; migrate clients to a consent-aware API."""
    type: Literal["IMAGE", "VIDEO"]
    prompt: str = Field(min_length=1, max_length=MAX_PROMPT_LENGTH)
    negativePrompt: str = Field(default="", max_length=MAX_PROMPT_LENGTH)
    workflow: dict[str, Any] = Field(description="Legacy compatibility input; dispatch is disabled")

class CreateJobV2(BaseModel):
    model_config = ConfigDict(extra="forbid")
    workflow_id: str = Field(min_length=1, max_length=64, pattern=r"^[a-z][a-z0-9_-]{0,63}$")
    parameters: dict[str, Any] = Field(default_factory=dict)
    grant_id: str = Field(min_length=1, max_length=256)
    session_id: str = Field(min_length=1, max_length=256)
    task_id: str = Field(min_length=1, max_length=256)
    client_id: str | None = Field(default=None, min_length=1, max_length=256)

async def authorize(authorization: str | None = Header(default=None)) -> str:
    if not API_KEY:
        raise HTTPException(status_code=503, detail="Backend API_KEY is not configured")
    expected = f"Bearer {API_KEY}"
    if authorization is None or not hmac.compare_digest(authorization, expected):
        raise HTTPException(status_code=401, detail="Unauthorized")
    return INSTANCE_OWNER_ID

async def comfy_request(method: str, path: str, **kwargs: Any) -> Any:
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(30.0, connect=5.0)) as client:
            response = await client.request(method, f"{COMFYUI}{path}", **kwargs)
            response.raise_for_status()
            return response.json() if response.content else {}
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"ComfyUI request failed ({exc.__class__.__name__})") from exc

def store_job(
    prompt_id: str,
    client_id: str,
    media_type: str,
    *,
    workflow_id: str | None = None,
    workflow_version: int | None = None,
    parameters: dict[str, Any] | None = None,
) -> dict[str, Any]:
    if not prompt_id:
        raise HTTPException(status_code=502, detail="ComfyUI did not return prompt_id")
    if JOB_STORE.count() >= MAX_TRACKED_JOBS:
        raise HTTPException(status_code=503, detail="Job capacity reached; restart or clear completed jobs")
    job_id = str(uuid.uuid4())
    job = {"id": job_id, "prompt_id": prompt_id, "client_id": client_id,
           "owner_id": INSTANCE_OWNER_ID, "type": media_type, "status": "QUEUED",
           "progress": 0.0, "workflow_id": workflow_id, "workflow_version": workflow_version,
           "parameters": parameters or {}, "outputs": []}
    try:
        JOB_STORE.create(job)
    except Exception as exc:
        raise HTTPException(status_code=503, detail="Job persistence failed") from exc
    jobs[job_id] = job
    return job

def owned_job(job_id: str, principal_id: str) -> dict[str, Any]:
    job = JOB_STORE.get(job_id)
    if job is None or not hmac.compare_digest(str(job.get("owner_id", "")), principal_id):
        raise HTTPException(status_code=404, detail="Job not found")
    return job

@app.get("/api/ready")
async def readiness():
    """Return 200 only when required local configuration and ComfyUI are usable."""
    if not API_KEY:
        raise HTTPException(status_code=503, detail="Backend API_KEY is not configured")
    try:
        await comfy_request("GET", "/system_stats")
    except HTTPException as exc:
        raise HTTPException(status_code=503, detail="ComfyUI is not ready") from exc
    try:
        JOB_STORE.count()
        CONSENT_STORE.get("__readiness_probe__")
    except Exception as exc:
        raise HTTPException(status_code=503, detail="Persistent stores are not ready") from exc
    return {"status": "ready"}

@app.get("/api/health")
async def health():
    try:
        await comfy_request("GET", "/system_stats")
        return {"status": "ok", "comfyui": "reachable"}
    except HTTPException:
        return {"status": "degraded", "comfyui": "unreachable"}

@app.get("/api/v2/workflows", dependencies=[Depends(authorize)])
async def list_workflows():
    """Return only server-configured workflows; never expose template graphs."""
    return {"workflows": registry.public_list()}

@app.post("/api/v2/jobs", status_code=201)
async def create_job_v2(request: CreateJobV2, principal_id: str = Depends(authorize)):
    """Dispatch a server-owned workflow only after exact, task-scoped consent."""
    try:
        graph = registry.build(request.workflow_id, request.parameters)
        authorize_dispatch(
            CONSENT_STORE,
            DispatchAuthorization(
                grant_id=request.grant_id,
                subject_id=principal_id,
                workflow_id=request.workflow_id,
                resource=workflow_resource(request.workflow_id, request.parameters),
                task_id=request.task_id,
                session_id=request.session_id,
            ),
            request.parameters,
        )
    except ConsentError as exc:
        raise HTTPException(status_code=403, detail="Consent denied") from exc

    client_id = request.client_id or str(uuid.uuid4())
    response = await comfy_request("POST", "/prompt", json={"prompt": graph, "client_id": client_id})
    prompt_id = response.get("prompt_id") if isinstance(response, dict) else None
    spec = registry.get_spec(request.workflow_id)
    return store_job(prompt_id, client_id, spec.media_type, workflow_id=spec.id, workflow_version=spec.version, parameters=request.parameters)

@app.post("/api/jobs", dependencies=[Depends(authorize)])
async def create_job(request: CreateJob):
    """Legacy arbitrary-graph dispatch is permanently disabled."""
    raise HTTPException(status_code=410, detail={
        "code": "LEGACY_DISPATCH_DISABLED",
        "message": "Legacy arbitrary workflow dispatch is disabled. Use the consent-aware v2 API when available.",
    })

@app.get("/api/jobs")
async def list_jobs(principal_id: str = Depends(authorize)):
    return JOB_STORE.list_owned(principal_id, MAX_TRACKED_JOBS)

@app.get("/api/jobs/{job_id}")
async def get_job(job_id: str, principal_id: str = Depends(authorize)):
    # Ownership is checked before any upstream ComfyUI request.
    job = owned_job(job_id, principal_id)
    history = await comfy_request("GET", f"/history/{job['prompt_id']}")
    item = history.get(job["prompt_id"], {})
    status = item.get("status", {})
    if status.get("completed"):
        job["status"] = "COMPLETED"
        outputs = item.get("outputs", {})
        files = []
        for node in outputs.values():
            if isinstance(node, dict):
                for key in ("images", "videos", "gifs"):
                    value = node.get(key, [])
                    if isinstance(value, list):
                        files.extend(value)
        job["outputs"] = files
        job["progress"] = 1.0
        JOB_STORE.update(job_id, status="COMPLETED", progress=1.0, outputs=files)
    elif status.get("status_str") == "error":
        job["status"] = "FAILED"
        JOB_STORE.update(
            job_id,
            status="FAILED",
            error_code="COMFYUI_JOB_FAILED",
            error_detail="ComfyUI reported a job failure",
        )
    else:
        JOB_STORE.update(job_id, status=job.get("status", "QUEUED"), progress=float(job.get("progress", 0.0)))
    return JOB_STORE.get(job_id) or job

@app.get("/api/jobs/{job_id}/result")
async def get_result(job_id: str, principal_id: str = Depends(authorize)):
    # Do not delegate before authorization: avoid any upstream call for foreign IDs.
    owned_job(job_id, principal_id)
    job = await get_job(job_id, principal_id)
    if job["status"] != "COMPLETED":
        raise HTTPException(status_code=409, detail="Job is not complete")
    return {"id": job_id, "outputs": job.get("outputs", [])}
