from __future__ import annotations
import hmac
import os
import time
import uuid
from dataclasses import dataclass, asdict
from typing import Any
from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field

router = APIRouter(prefix="/api/omni", tags=["omni"])
API_KEY = os.getenv("API_KEY", "").strip()
MAX_GENERATIONS_PER_HOUR = int(os.getenv("MAX_GENERATIONS_PER_HOUR", "60"))
MAX_ESTIMATED_COST_PER_HOUR = float(os.getenv("MAX_ESTIMATED_COST_PER_HOUR", "25.0"))

@dataclass(frozen=True)
class RuntimeSlot:
    id: str
    title: str
    purpose: str
    format: str
    resident: bool = True

RUNTIME_SLOTS = (
    RuntimeSlot("chat-code", "CHAT + CODE", "chat, JARVIS and coding", "GGUF"),
    RuntimeSlot("image", "IMAGE", "image generation / image pipeline", "GGUF"),
    RuntimeSlot("video", "VIDEO", "video generation / video pipeline", "GGUF"),
)

@dataclass
class SlotState:
    slot_id: str
    loaded: bool = False
    model_path: str | None = None
    model_name: str | None = None
    memory_mb: int | None = None
    updated_at: float = 0.0

_SLOT_STATE = {slot.id: SlotState(slot.id) for slot in RUNTIME_SLOTS}
_USAGE: list[tuple[float, float]] = []

class SlotLoadRequest(BaseModel):
    model_path: str = Field(min_length=1, max_length=1024)
    model_name: str = Field(min_length=1, max_length=256)
    memory_mb: int | None = Field(default=None, ge=1, le=1024 * 1024)

class ResidentModel(BaseModel):
    model_path: str = Field(min_length=1, max_length=1024)
    model_name: str = Field(min_length=1, max_length=256)
    memory_mb: int | None = Field(default=None, ge=1, le=1024 * 1024)

class LoadAllRequest(BaseModel):
    slots: list[ResidentModel] = Field(min_length=3, max_length=3)

class GenerateGuardRequest(BaseModel):
    slot_id: str = Field(min_length=1, max_length=64)
    estimated_cost: float = Field(default=0.0, ge=0.0, le=100000.0)

def _auth(authorization: str | None) -> None:
    if not API_KEY:
        raise HTTPException(status_code=503, detail="Backend API_KEY is not configured")
    if authorization is None or not hmac.compare_digest(authorization, f"Bearer {API_KEY}"):
        raise HTTPException(status_code=401, detail="Unauthorized")

def _slot(slot_id: str) -> RuntimeSlot:
    for slot in RUNTIME_SLOTS:
        if slot.id == slot_id:
            return slot
    raise HTTPException(status_code=404, detail="Unknown runtime slot")

def _prune_usage() -> None:
    cutoff = time.time() - 3600
    while _USAGE and _USAGE[0][0] < cutoff:
        _USAGE.pop(0)

@router.get("/runtime")
async def runtime_state(authorization: str | None = Header(default=None)) -> dict[str, Any]:
    _auth(authorization)
    return {"slots": [{**asdict(slot), "state": asdict(_SLOT_STATE[slot.id])} for slot in RUNTIME_SLOTS], "simultaneous_resident_slots": 3}

@router.post("/runtime/{slot_id}/load")
async def load_slot(slot_id: str, request: SlotLoadRequest, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    _auth(authorization)
    _slot(slot_id)
    state = _SLOT_STATE[slot_id]
    state.loaded = True
    state.model_path = request.model_path
    state.model_name = request.model_name
    state.memory_mb = request.memory_mb
    state.updated_at = time.time()
    return {"status": "loaded", "slot": asdict(state)}

@router.post("/runtime/load-all")
async def load_all(request: LoadAllRequest, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    """Atomically register all three resident model slots."""
    _auth(authorization)
    by_slot = {slot.id: item for slot, item in zip(RUNTIME_SLOTS, request.slots)}
    if len(by_slot) != 3:
        raise HTTPException(status_code=422, detail="Exactly three resident slots are required")
    for slot in RUNTIME_SLOTS:
        item = by_slot[slot.id]
        if slot.format == "GGUF" and not item.model_name.lower().endswith(".gguf"):
            raise HTTPException(status_code=422, detail=f"{slot.id} requires a GGUF model")
    now = time.time()
    for slot in RUNTIME_SLOTS:
        item = by_slot[slot.id]
        state = _SLOT_STATE[slot.id]
        state.loaded = True
        state.model_path = item.model_path
        state.model_name = item.model_name
        state.memory_mb = item.memory_mb
        state.updated_at = now
    return {
        "status": "loaded",
        "simultaneous_resident_slots": 3,
        "slots": [{**asdict(slot), "state": asdict(_SLOT_STATE[slot.id])} for slot in RUNTIME_SLOTS],
    }

@router.post("/runtime/{slot_id}/unload")
async def unload_slot(slot_id: str, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    _auth(authorization)
    _slot(slot_id)
    state = _SLOT_STATE[slot_id]
    state.loaded = False
    state.updated_at = time.time()
    return {"status": "unloaded", "slot": asdict(state)}

@router.post("/generation/authorize")
async def authorize_generation(request: GenerateGuardRequest, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    _auth(authorization)
    _slot(request.slot_id)
    _prune_usage()
    hourly_cost = sum(cost for _, cost in _USAGE)
    if len(_USAGE) >= MAX_GENERATIONS_PER_HOUR:
        raise HTTPException(status_code=429, detail="Generation rate limit reached")
    if hourly_cost + request.estimated_cost > MAX_ESTIMATED_COST_PER_HOUR:
        raise HTTPException(status_code=429, detail="Generation spend limit reached")
    receipt_id = str(uuid.uuid4())
    _USAGE.append((time.time(), request.estimated_cost))
    return {"authorized": True, "receipt_id": receipt_id, "slot_id": request.slot_id,
            "limits": {"generations_per_hour": MAX_GENERATIONS_PER_HOUR, "estimated_cost_per_hour": MAX_ESTIMATED_COST_PER_HOUR}}
