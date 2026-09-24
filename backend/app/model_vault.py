from __future__ import annotations

import hmac
import os
import re
from pathlib import Path
from typing import Any

import httpx
from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field

router = APIRouter(prefix="/api/models", tags=["models"])

MODEL_ROOT = Path(os.getenv("MODEL_ROOT", "/models")).resolve()
HF_BASE = "https://huggingface.co"
MAX_DOWNLOAD_BYTES = int(os.getenv("MODEL_MAX_DOWNLOAD_BYTES", str(200 * 1024**3)))
API_KEY = os.getenv("API_KEY", "").strip()
HF_REPO_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")


class HFDownloadRequest(BaseModel):
    repo_id: str = Field(min_length=3, max_length=200)
    filename: str = Field(min_length=1, max_length=500)
    revision: str = Field(default="main", min_length=1, max_length=100)


async def _authorize(authorization: str | None) -> None:
    if not API_KEY:
        raise HTTPException(status_code=503, detail="Backend API_KEY is not configured")
    if authorization is None or not hmac.compare_digest(authorization, f"Bearer {API_KEY}"):
        raise HTTPException(status_code=401, detail="Unauthorized")


def _safe_model_path(path: str) -> Path:
    candidate = (MODEL_ROOT / path.lstrip("/")).resolve()
    if MODEL_ROOT != candidate and MODEL_ROOT not in candidate.parents:
        raise HTTPException(status_code=400, detail="Invalid model path")
    return candidate


@router.get("/hf/search")
async def search_huggingface(q: str = "", limit: int = 20, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    await _authorize(authorization)
    limit = max(1, min(limit, 50))
    async with httpx.AsyncClient(timeout=20.0) as client:
        response = await client.get(f"{HF_BASE}/api/models", params={"search": q, "limit": limit, "full": "true"})
    if response.status_code >= 400:
        raise HTTPException(status_code=502, detail="Hugging Face search failed")
    return {"models": [
        {"id": item.get("id"), "private": item.get("private", False), "downloads": item.get("downloads"), "likes": item.get("likes"), "tags": item.get("tags", []), "url": f"{HF_BASE}/{item.get('id')}"}
        for item in response.json() if isinstance(item, dict)
    ]}


@router.post("/hf/download")
async def download_huggingface(request: HFDownloadRequest, authorization: str | None = Header(default=None)):
    await _authorize(authorization)
    if not HF_REPO_RE.fullmatch(request.repo_id):
        raise HTTPException(status_code=400, detail="Invalid Hugging Face repo id")
    if ".." in Path(request.filename).parts:
        raise HTTPException(status_code=400, detail="Invalid filename")
    url = f"{HF_BASE}/{request.repo_id}/resolve/{request.revision}/{request.filename}"
    target = _safe_model_path(request.filename)
    target.parent.mkdir(parents=True, exist_ok=True)
    async with httpx.AsyncClient(timeout=None, follow_redirects=True) as client:
        async with client.stream("GET", url, headers={"Accept": "application/octet-stream"}) as response:
            if response.status_code >= 400:
                raise HTTPException(status_code=response.status_code, detail="Hugging Face download failed")
            length = response.headers.get("content-length")
            if length and int(length) > MAX_DOWNLOAD_BYTES:
                raise HTTPException(status_code=413, detail="Model exceeds configured download limit")
            total = 0
            tmp = target.with_suffix(target.suffix + ".part")
            with tmp.open("wb") as handle:
                async for chunk in response.aiter_bytes(1024 * 1024):
                    total += len(chunk)
                    if total > MAX_DOWNLOAD_BYTES:
                        tmp.unlink(missing_ok=True)
                        raise HTTPException(status_code=413, detail="Model exceeds configured download limit")
                    handle.write(chunk)
            tmp.replace(target)
    return {"status": "downloaded", "path": str(target), "bytes": target.stat().st_size, "repo_id": request.repo_id, "filename": request.filename}
