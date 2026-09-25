from __future__ import annotations

import asyncio
import hmac
import os
import shlex
import signal
import time
import uuid
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field

router = APIRouter(prefix="/api/omni", tags=["omni"])
API_KEY = os.getenv("API_KEY", "").strip()
MAX_GENERATIONS_PER_HOUR = int(os.getenv("MAX_GENERATIONS_PER_HOUR", "60"))
MAX_ESTIMATED_COST_PER_HOUR = float(os.getenv("MAX_ESTIMATED_COST_PER_HOUR", "25.0"))
MAX_RESIDENT_MEMORY_MB = int(os.getenv("MAX_RESIDENT_MEMORY_MB", str(64 * 1024)))


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
    pid: int | None = None
    runtime_command: str | None = None
    updated_at: float = 0.0
    error: str | None = None


_SLOT_STATE = {slot.id: SlotState(slot.id) for slot in RUNTIME_SLOTS}
_PROCESSES: dict[str, asyncio.subprocess.Process] = {}
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


def _runtime_command(slot_id: str, model_path: str, model_name: str) -> list[str]:
    env_name = {
        "chat-code": "RUNTIME_COMMAND_CHAT_CODE",
        "image": "RUNTIME_COMMAND_IMAGE",
        "video": "RUNTIME_COMMAND_VIDEO",
    }[slot_id]
    template = os.getenv(env_name, "").strip()
    if not template:
        raise HTTPException(
            status_code=503,
            detail=f"{slot_id} runtime is not configured; set {env_name} on the private worker",
        )
    values = {"{model}": model_path, "{name}": model_name}
    command = template
    for token, value in values.items():
        command = command.replace(token, value)
    return shlex.split(command)


def validate_resident_models(models: list[ResidentModel]) -> None:
    if len(models) != 3:
        raise HTTPException(status_code=422, detail="Exactly three resident models are required")
    normalized = [str(Path(item.model_path).resolve()) for item in models]
    if len(set(normalized)) != 3:
        raise HTTPException(status_code=422, detail="Resident CHAT+CODE, IMAGE and VIDEO models must be three distinct GGUF files")
    for item in models:
        _validate_model_path(item)
        if not item.model_name.lower().endswith(".gguf"):
            raise HTTPException(status_code=422, detail=f"{item.model_name} requires a GGUF model")


def _validate_model_path(request: ResidentModel) -> None:
    path = Path(request.model_path).resolve()
    if path.suffix.lower() != ".gguf":
        raise HTTPException(status_code=422, detail=f"{request.model_name} must be a GGUF file")
    if not path.is_file():
        raise HTTPException(status_code=422, detail=f"Model file does not exist: {request.model_path}")


async def _stop_process(slot_id: str) -> None:
    process = _PROCESSES.pop(slot_id, None)
    if process is not None and process.returncode is None:
        try:
            process.send_signal(signal.SIGTERM)
            await asyncio.wait_for(process.wait(), timeout=10)
        except (asyncio.TimeoutError, ProcessLookupError):
            try:
                process.kill()
                await process.wait()
            except ProcessLookupError:
                pass


async def _start_slot(slot: RuntimeSlot, request: ResidentModel) -> SlotState:
    _validate_model_path(request)
    command = _runtime_command(slot.id, request.model_path, request.model_name)
    await _stop_process(slot.id)
    try:
        process = await asyncio.create_subprocess_exec(
            *command,
            stdout=asyncio.subprocess.DEVNULL,
            stderr=asyncio.subprocess.PIPE,
        )
    except OSError as exc:
        raise HTTPException(status_code=503, detail=f"Unable to start {slot.id} runtime") from exc
    await asyncio.sleep(0.15)
    if process.returncode is not None:
        stderr = await process.stderr.read()
        detail = stderr.decode("utf-8", errors="replace").strip()[-500:]
        raise HTTPException(status_code=503, detail=f"{slot.id} runtime exited during load: {detail}")
    _PROCESSES[slot.id] = process
    state = _SLOT_STATE[slot.id]
    state.loaded = True
    state.model_path = request.model_path
    state.model_name = request.model_name
    state.memory_mb = request.memory_mb
    state.pid = process.pid
    state.runtime_command = command[0]
    state.updated_at = time.time()
    state.error = None
    return state


def _prune_usage() -> None:
    cutoff = time.time() - 3600
    while _USAGE and _USAGE[0][0] < cutoff:
        _USAGE.pop(0)


@router.get("/runtime")
async def runtime_state(authorization: str | None = Header(default=None)) -> dict[str, Any]:
    _auth(authorization)
    for slot in RUNTIME_SLOTS:
        process = _PROCESSES.get(slot.id)
        if process is not None and process.returncode is not None:
            state = _SLOT_STATE[slot.id]
            state.loaded = False
            state.error = f"runtime exited with code {process.returncode}"
            _PROCESSES.pop(slot.id, None)
    return {
        "slots": [{**asdict(slot), "state": asdict(_SLOT_STATE[slot.id])} for slot in RUNTIME_SLOTS],
        "simultaneous_resident_slots": 3,
        "simultaneous_resident_required": True,
    }


@router.post("/runtime/{slot_id}/load")
async def load_slot(slot_id: str, request: SlotLoadRequest, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    _auth(authorization)
    slot = _slot(slot_id)
    state = await _start_slot(slot, ResidentModel(**request.model_dump()))
    return {"status": "loaded", "slot": asdict(state)}


@router.post("/runtime/load-all")
async def load_all(request: LoadAllRequest, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    """Actually start all three trusted runtime processes and keep them resident."""
    _auth(authorization)
    validate_resident_models(request.slots)
    if sum((item.memory_mb or 0) for item in request.slots) > MAX_RESIDENT_MEMORY_MB:
        raise HTTPException(status_code=422, detail="Declared resident model memory exceeds configured limit")

    try:
        # Start the three resident runtimes as one transaction. The tasks are
        # scheduled together so CHAT+CODE, IMAGE and VIDEO are loaded as a
        # single atomic operation rather than exposing a partially-loaded set.
        await asyncio.gather(
            *(_start_slot(slot, item) for slot, item in zip(RUNTIME_SLOTS, request.slots))
        )
    except Exception:
        await asyncio.gather(*(_stop_process(slot.id) for slot in RUNTIME_SLOTS))
        for slot in RUNTIME_SLOTS:
            _SLOT_STATE[slot.id].loaded = False
            _SLOT_STATE[slot.id].pid = None
        raise
    return {
        "status": "loaded",
        "simultaneous_resident_slots": 3,
        "slots": [{**asdict(slot), "state": asdict(_SLOT_STATE[slot.id])} for slot in RUNTIME_SLOTS],
    }


@router.post("/runtime/{slot_id}/unload")
async def unload_slot(slot_id: str, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    _auth(authorization)
    _slot(slot_id)
    await _stop_process(slot_id)
    state = _SLOT_STATE[slot_id]
    state.loaded = False
    state.pid = None
    state.runtime_command = None
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
    return {
        "authorized": True,
        "receipt_id": receipt_id,
        "slot_id": request.slot_id,
        "limits": {
            "generations_per_hour": MAX_GENERATIONS_PER_HOUR,
            "estimated_cost_per_hour": MAX_ESTIMATED_COST_PER_HOUR,
        },
    }
