from __future__ import annotations

import hmac
import os
import uuid
from typing import Literal, Any

import httpx
from fastapi import FastAPI, Header, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from .workflow_registry import registry

COMFYUI = os.getenv("COMFYUI_BASE_URL", "http://127.0.0.1:8188").rstrip("/")
API_KEY = os.getenv("API_KEY", "").strip()
MAX_PROMPT_LENGTH = int(os.getenv("MAX_PROMPT_LENGTH", "4000"))
MAX_WORKFLOW_NODES = int(os.getenv("MAX_WORKFLOW_NODES", "250"))
MAX_TRACKED_JOBS = int(os.getenv("MAX_TRACKED_JOBS", "500"))

app = FastAPI(title="AI Studio API", version="0.2.0")
origins = [x.strip() for x in os.getenv("CORS_ORIGINS", "").split(",") if x.strip()]
app.add_middleware(CORSMiddleware, allow_origins=origins or [], allow_credentials=False,
                   allow_methods=["GET", "POST"], allow_headers=["Authorization", "Content-Type"])

# Demo in-memory index. Use PostgreSQL/Redis before multi-user production deployment.
jobs: dict[str, dict[str, Any]] = {}

class CreateJob(BaseModel):
    """Legacy v1 request. Kept temporarily for existing clients."""
    type: Literal["IMAGE", "VIDEO"]
    prompt: str = Field(min_length=1, max_length=MAX_PROMPT_LENGTH)
    negativePrompt: str = Field(default="", max_length=MAX_PROMPT_LENGTH)
    workflow: dict[str, Any] = Field(description="Legacy compatibility input; migrate to server-owned workflow IDs")

class CreateJobV2(BaseModel):
    workflow_id: str = Field(min_length=1, max_length=64, pattern=r"^[a-z][a-z0-9_-]{0,63}$")
    parameters: dict[str, Any] = Field(default_factory=dict)

async def authorize(authorization: str | None = Header(default=None)) -> None:
    if not API_KEY:
        raise HTTPException(status_code=503, detail="Backend API_KEY is not configured")
    expected = f"Bearer {API_KEY}"
    if authorization is None or not hmac.compare_digest(authorization, expected):
        raise HTTPException(status_code=401, detail="Unauthorized")

async def comfy_request(method: str, path: str, **kwargs: Any) -> Any:
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(30.0, connect=5.0)) as client:
            response = await client.request(method, f"{COMFYUI}{path}", **kwargs)
            response.raise_for_status()
            return response.json() if response.content else {}
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"ComfyUI request failed ({exc.__class__.__name__})") from exc

def store_job(prompt_id: str, client_id: str, media_type: str) -> dict[str, Any]:
    if not prompt_id:
        raise HTTPException(status_code=502, detail="ComfyUI did not return prompt_id")
    if len(jobs) >= MAX_TRACKED_JOBS:
        raise HTTPException(status_code=503, detail="Job capacity reached; restart or clear completed jobs")
    job_id = str(uuid.uuid4())
    job = {"id": job_id, "prompt_id": prompt_id, "client_id": client_id,
           "type": media_type, "status": "QUEUED", "progress": 0.0}
    jobs[job_id] = job
    return job

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

@app.post("/api/v2/jobs", dependencies=[Depends(authorize)])
async def create_job_v2(request: CreateJobV2):
    # Build from a server-owned template. No client-supplied graph is accepted here.
    graph = registry.build(request.workflow_id, request.parameters)
    if len(graph) > MAX_WORKFLOW_NODES:
        raise HTTPException(status_code=422, detail="Workflow exceeds backend node limit")
    if len(jobs) >= MAX_TRACKED_JOBS:
        raise HTTPException(status_code=503, detail="Job capacity reached; restart or clear completed jobs")
    client_id = str(uuid.uuid4())
    result = await comfy_request("POST", "/prompt", json={"prompt": graph, "client_id": client_id})
    spec = next((item for item in registry.public_list() if item["id"] == request.workflow_id), None)
    media_type = spec["type"] if spec else "IMAGE"
    return store_job(result.get("prompt_id"), client_id, media_type)

@app.post("/api/jobs", dependencies=[Depends(authorize)])
async def create_job(request: CreateJob):
    if not request.workflow:
        raise HTTPException(status_code=422, detail="Workflow must not be empty")
    if len(request.workflow) > MAX_WORKFLOW_NODES:
        raise HTTPException(status_code=413, detail="Workflow has too many nodes")
    if len(jobs) >= MAX_TRACKED_JOBS:
        raise HTTPException(status_code=503, detail="Job capacity reached; restart or clear completed jobs")
    # Legacy security boundary remains: migrate clients to /api/v2/jobs before disabling v1.
    client_id = str(uuid.uuid4())
    result = await comfy_request("POST", "/prompt", json={"prompt": request.workflow, "client_id": client_id})
    return store_job(result.get("prompt_id"), client_id, request.type)

@app.get("/api/jobs", dependencies=[Depends(authorize)])
async def list_jobs():
    return list(jobs.values())

@app.get("/api/jobs/{job_id}", dependencies=[Depends(authorize)])
async def get_job(job_id: str):
    job = jobs.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
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
    elif status.get("status_str") == "error":
        job["status"] = "FAILED"
    return job

@app.get("/api/jobs/{job_id}/result", dependencies=[Depends(authorize)])
async def get_result(job_id: str):
    job = await get_job(job_id)
    if job["status"] != "COMPLETED":
        raise HTTPException(status_code=409, detail="Job is not complete")
    return {"id": job_id, "outputs": job.get("outputs", [])}
