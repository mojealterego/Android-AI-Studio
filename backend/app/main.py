from __future__ import annotations

import os
import uuid
from typing import Literal, Any

import httpx
from fastapi import FastAPI, Header, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

COMFYUI = os.getenv("COMFYUI_BASE_URL", "http://127.0.0.1:8188").rstrip("/")
API_KEY = os.getenv("API_KEY", "")
MAX_PROMPT_LENGTH = int(os.getenv("MAX_PROMPT_LENGTH", "4000"))

app = FastAPI(title="AI Studio API", version="0.1.0")
origins = [x.strip() for x in os.getenv("CORS_ORIGINS", "").split(",") if x.strip()]
app.add_middleware(CORSMiddleware, allow_origins=origins or [], allow_credentials=False,
                   allow_methods=["GET", "POST"], allow_headers=["Authorization", "Content-Type"])

# Demo in-memory index. Use PostgreSQL/Redis before multi-user production deployment.
jobs: dict[str, dict[str, Any]] = {}

class CreateJob(BaseModel):
    type: Literal["IMAGE", "VIDEO"]
    prompt: str = Field(min_length=1)
    negativePrompt: str = ""
    workflow: dict[str, Any] = Field(description="Validated ComfyUI API-format workflow")

async def authorize(authorization: str | None = Header(default=None)) -> None:
    if API_KEY and authorization != f"Bearer {API_KEY}":
        raise HTTPException(status_code=401, detail="Unauthorized")

async def comfy_request(method: str, path: str, **kwargs: Any) -> Any:
    try:
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.request(method, f"{COMFYUI}{path}", **kwargs)
            response.raise_for_status()
            return response.json() if response.content else {}
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"ComfyUI unavailable: {exc.__class__.__name__}") from exc

@app.get("/api/health")
async def health():
    try:
        await comfy_request("GET", "/system_stats")
        return {"status": "ok", "comfyui": "reachable"}
    except HTTPException:
        return {"status": "degraded", "comfyui": "unreachable"}

@app.post("/api/jobs", dependencies=[Depends(authorize)])
async def create_job(request: CreateJob):
    if len(request.prompt) > MAX_PROMPT_LENGTH or len(request.negativePrompt) > MAX_PROMPT_LENGTH:
        raise HTTPException(status_code=413, detail="Prompt too long")
    # Do not accept arbitrary client workflows in production. Map workflow IDs to
    # server-owned templates and validate node types/inputs before queueing.
    client_id = str(uuid.uuid4())
    result = await comfy_request("POST", "/prompt", json={"prompt": request.workflow, "client_id": client_id})
    prompt_id = result.get("prompt_id")
    if not prompt_id:
        raise HTTPException(status_code=502, detail="ComfyUI did not return prompt_id")
    job_id = str(uuid.uuid4())
    jobs[job_id] = {"id": job_id, "prompt_id": prompt_id, "client_id": client_id,
                    "type": request.type, "status": "QUEUED", "progress": 0.0}
    return jobs[job_id]

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
            for key in ("images", "videos", "gifs"):
                files.extend(node.get(key, []))
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
