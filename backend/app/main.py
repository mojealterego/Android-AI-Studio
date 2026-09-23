from __future__ import annotations

import hmac
import os
import uuid
from pathlib import PurePosixPath
from typing import Literal, Any
from datetime import datetime, timedelta, timezone

import httpx
from fastapi import FastAPI, Header, HTTPException, Depends
from fastapi.responses import StreamingResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, ConfigDict

from .workflow_registry import registry
from .consent_dispatch import DispatchAuthorization, authorize_dispatch
from .consent_store import ConsentStore
from .consent_core import ConsentError, Duration, Access, Grant
from .consent_binding import workflow_resource, action_digest
from .consent_receipt import ActionReceipt, ActionStatus
from .receipt_store import ReceiptStore
from .approval_auth import ApprovalAuthenticationError, authenticate_approver
from .job_store import JobStore
from .job_control import make_job_router

COMFYUI = os.getenv("COMFYUI_BASE_URL", "http://127.0.0.1:8188").rstrip("/")
API_KEY = os.getenv("API_KEY", "").strip()
INSTANCE_OWNER_ID = os.getenv("INSTANCE_OWNER_ID", "single-instance").strip()
MAX_PROMPT_LENGTH = int(os.getenv("MAX_PROMPT_LENGTH", "4000"))
MAX_WORKFLOW_NODES = int(os.getenv("MAX_WORKFLOW_NODES", "250"))
MAX_TRACKED_JOBS = int(os.getenv("MAX_TRACKED_JOBS", "500"))
CONSENT_DB_PATH = os.getenv("CONSENT_DB_PATH", "./data/consent.sqlite3")
CONSENT_STORE = ConsentStore(CONSENT_DB_PATH)
JOB_DB_PATH = os.getenv("JOB_DB_PATH", "./data/jobs.sqlite3")
JOB_STORE = JobStore(JOB_DB_PATH)
RECEIPT_DB_PATH = os.getenv("RECEIPT_DB_PATH", "./data/receipts.sqlite3")
RECEIPT_STORE = ReceiptStore(RECEIPT_DB_PATH)
MEDIA_MAX_BYTES = int(os.getenv("MEDIA_MAX_BYTES", str(250 * 1024 * 1024)))

app = FastAPI(title="AI Studio API", version="0.3.0")
origins = [x.strip() for x in os.getenv("CORS_ORIGINS", "").split(",") if x.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins or [],
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["Authorization", "X-Approval-Token", "Content-Type"],
)

JOB_STORE.recover_inflight(INSTANCE_OWNER_ID)
jobs: dict[str, dict[str, Any]] = {
    job["id"]: job for job in JOB_STORE.list_owned(INSTANCE_OWNER_ID, MAX_TRACKED_JOBS)
}


class CreateJob(BaseModel):
    type: Literal["IMAGE", "VIDEO"]
    prompt: str = Field(min_length=1, max_length=MAX_PROMPT_LENGTH)
    negativePrompt: str = Field(default="", max_length=MAX_PROMPT_LENGTH)
    workflow: dict[str, Any] = Field(description="Legacy compatibility input; dispatch is disabled")


class PreviewRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    workflow_id: str = Field(min_length=1, max_length=64, pattern=r"^[a-z][a-z0-9_-]{0,63}$")
    parameters: dict[str, Any] = Field(default_factory=dict)
    session_id: str = Field(min_length=1, max_length=256)
    task_id: str = Field(min_length=1, max_length=256)


class ApprovalRequest(PreviewRequest):
    preview_digest: str = Field(min_length=64, max_length=64, pattern=r"^[0-9a-f]{64}$")
    duration: Duration = Duration.ONCE
    expires_in_seconds: int = Field(default=300, ge=30, le=900)


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


async def authorize_actor(
    authorization: str | None = Header(default=None),
    approval_token: str | None = Header(default=None, alias="X-Approval-Token"),
) -> str:
    await authorize(authorization)
    if approval_token:
        try:
            return authenticate_approver(approval_token)
        except ApprovalAuthenticationError as exc:
            raise HTTPException(status_code=403, detail="Approval authentication failed") from exc
    return INSTANCE_OWNER_ID


async def approver_subject(
    authorization: str | None = Header(default=None),
    approval_token: str | None = Header(default=None, alias="X-Approval-Token"),
) -> str:
    await authorize(authorization)
    try:
        return authenticate_approver(approval_token)
    except ApprovalAuthenticationError as exc:
        raise HTTPException(status_code=403, detail="Human approval credential required") from exc


async def comfy_request(method: str, path: str, **kwargs: Any) -> Any:
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(30.0, connect=5.0)) as client:
            response = await client.request(method, f"{COMFYUI}{path}", **kwargs)
            response.raise_for_status()
            return response.json() if response.content else {}
    except (httpx.TimeoutException, httpx.ConnectError, httpx.RemoteProtocolError) as exc:
        raise HTTPException(
            status_code=502,
            detail={"code": "COMFYUI_AMBIGUOUS", "type": exc.__class__.__name__},
        ) from exc
    except httpx.HTTPStatusError as exc:
        raise HTTPException(
            status_code=502,
            detail={"code": "COMFYUI_REJECTED", "status": exc.response.status_code},
        ) from exc
    except httpx.HTTPError as exc:
        raise HTTPException(
            status_code=502,
            detail={"code": "COMFYUI_AMBIGUOUS", "type": exc.__class__.__name__},
        ) from exc


def _receipt(
    *,
    actor_id: str,
    status: ActionStatus,
    resource: str,
    grant_id: str | None,
    task_id: str | None,
    request_digest: str | None,
    external_reference: str | None = None,
    error_code: str | None = None,
    provenance: str | None = None,
) -> ActionReceipt:
    return ActionReceipt(
        receipt_id=str(uuid.uuid4()),
        actor_id=actor_id,
        action_type="workflow.dispatch",
        resource=resource,
        status=status,
        occurred_at=datetime.now(timezone.utc),
        grant_id=grant_id,
        task_id=task_id,
        request_digest=request_digest,
        external_reference=external_reference,
        error_code=error_code,
        provenance=provenance,
    )


def store_job(
    prompt_id: str,
    client_id: str,
    media_type: str,
    *,
    workflow_id: str | None = None,
    workflow_version: int | None = None,
    parameters: dict[str, Any] | None = None,
    owner_id: str | None = None,
) -> dict[str, Any]:
    if not prompt_id:
        raise HTTPException(status_code=502, detail="ComfyUI did not return prompt_id")
    if JOB_STORE.count() >= MAX_TRACKED_JOBS:
        raise HTTPException(status_code=503, detail="Job capacity reached; restart or clear completed jobs")
    job_id = str(uuid.uuid4())
    job = {
        "id": job_id,
        "prompt_id": prompt_id,
        "client_id": client_id,
        "owner_id": owner_id or INSTANCE_OWNER_ID,
        "type": media_type,
        "status": "QUEUED",
        "progress": 0.0,
        "workflow_id": workflow_id,
        "workflow_version": workflow_version,
        "parameters": parameters or {},
        "outputs": [],
    }
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
    if not API_KEY:
        raise HTTPException(status_code=503, detail="Backend API_KEY is not configured")
    try:
        await comfy_request("GET", "/system_stats")
    except HTTPException as exc:
        raise HTTPException(status_code=503, detail="ComfyUI is not ready") from exc
    try:
        JOB_STORE.count()
        CONSENT_STORE.get("__readiness_probe__")
        RECEIPT_STORE.list_owned(INSTANCE_OWNER_ID, limit=1)
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
    return {"workflows": registry.public_list()}


@app.post("/api/v2/approval-preview")
async def approval_preview(
    request: PreviewRequest,
    principal_id: str = Depends(authorize),
):
    """Create a server-derived preview; no grant or external side effect is created."""
    graph = registry.build(request.workflow_id, request.parameters)
    spec = registry.get_spec(request.workflow_id)
    digest = action_digest(request.workflow_id, request.parameters)
    return {
        "workflow_id": spec.id,
        "workflow_version": spec.version,
        "media_type": spec.media_type,
        "parameters": request.parameters,
        "session_id": request.session_id,
        "task_id": request.task_id,
        "action": "write",
        "resource": workflow_resource(request.workflow_id, request.parameters),
        "preview_digest": digest,
        "consequence": "enqueue generation job in the private ComfyUI backend",
    }


@app.post("/api/v2/approvals", status_code=201)
async def create_approval(
    request: ApprovalRequest,
    approver: str = Depends(approver_subject),
):
    """Issue a scoped grant only after a separate approval credential is verified."""
    registry.build(request.workflow_id, request.parameters)
    expected_digest = action_digest(request.workflow_id, request.parameters)
    if not hmac.compare_digest(expected_digest, request.preview_digest):
        raise HTTPException(status_code=409, detail="Approval preview does not match request")
    if request.duration == Duration.ONCE and not request.task_id:
        raise HTTPException(status_code=422, detail="One-time approval requires a task")
    now = datetime.now(timezone.utc)
    expires = now + timedelta(seconds=request.expires_in_seconds)
    grant_id = str(uuid.uuid4())
    grant = Grant(
        subject_id=approver,
        resource=workflow_resource(request.workflow_id, request.parameters),
        access=frozenset({Access.WRITE}),
        duration=request.duration,
        created_at=now,
        expires_at=expires,
        session_id=request.session_id if request.duration in (Duration.ONCE, Duration.SESSION) else None,
        task_id=request.task_id if request.duration in (Duration.ONCE, Duration.TASK) else None,
    )
    try:
        CONSENT_STORE.create(grant_id, grant)
        RECEIPT_STORE.append(
            _receipt(
                actor_id=approver,
                status=ActionStatus.REQUESTED,
                resource=grant.resource,
                grant_id=grant_id,
                task_id=request.task_id,
                request_digest=expected_digest,
                provenance="human-approval-preview",
            )
        )
        RECEIPT_STORE.append(
            _receipt(
                actor_id=approver,
                status=ActionStatus.AUTHORIZED,
                resource=grant.resource,
                grant_id=grant_id,
                task_id=request.task_id,
                request_digest=expected_digest,
                provenance="human-approval-credential",
            )
        )
    except Exception as exc:
        raise HTTPException(status_code=503, detail="Approval persistence failed") from exc
    return {
        "grant_id": grant_id,
        "subject_id": approver,
        "resource": grant.resource,
        "duration": grant.duration.value,
        "expires_at": expires.isoformat(),
    }


async def create_job_v2(request: CreateJobV2, principal_id: str) -> dict[str, Any]:
    """Core dispatch function retained as a directly testable unit."""
    graph = registry.build(request.workflow_id, request.parameters)
    resource = workflow_resource(request.workflow_id, request.parameters)
    digest = action_digest(request.workflow_id, request.parameters)
    try:
        authorize_dispatch(
            CONSENT_STORE,
            DispatchAuthorization(
                grant_id=request.grant_id,
                subject_id=principal_id,
                workflow_id=request.workflow_id,
                resource=resource,
                task_id=request.task_id,
                session_id=request.session_id,
            ),
            request.parameters,
        )
    except ConsentError as exc:
        try:
            RECEIPT_STORE.append(
                _receipt(
                    actor_id=principal_id,
                    status=ActionStatus.DENIED,
                    resource=resource,
                    grant_id=request.grant_id,
                    task_id=request.task_id,
                    request_digest=digest,
                    provenance="dispatch-denied",
                )
            )
        except Exception:
            pass
        raise HTTPException(status_code=403, detail="Consent denied") from exc

    client_id = request.client_id or str(uuid.uuid4())
    try:
        prior = RECEIPT_STORE.list_owned(principal_id, task_id=request.task_id, limit=10)
        if not any(
            row["resource"] == resource and row["grant_id"] == request.grant_id
            and row["status"] == ActionStatus.AUTHORIZED.value
            for row in prior
        ):
            RECEIPT_STORE.append(
                _receipt(
                    actor_id=principal_id,
                    status=ActionStatus.REQUESTED,
                    resource=resource,
                    grant_id=request.grant_id,
                    task_id=request.task_id,
                    request_digest=digest,
                    provenance="dispatch-core",
                )
            )
            RECEIPT_STORE.append(
                _receipt(
                    actor_id=principal_id,
                    status=ActionStatus.AUTHORIZED,
                    resource=resource,
                    grant_id=request.grant_id,
                    task_id=request.task_id,
                    request_digest=digest,
                    provenance="grant-revalidated",
                )
            )
        RECEIPT_STORE.append(
            _receipt(
                actor_id=principal_id,
                status=ActionStatus.DISPATCHED,
                resource=resource,
                grant_id=request.grant_id,
                task_id=request.task_id,
                request_digest=digest,
                external_reference=client_id,
                provenance="pre-dispatch",
            )
        )
        response = await comfy_request(
            "POST",
            "/prompt",
            json={"prompt": graph, "client_id": client_id},
        )
    except HTTPException as exc:
        detail = exc.detail if isinstance(exc.detail, dict) else {}
        status = ActionStatus.UNKNOWN if detail.get("code") == "COMFYUI_AMBIGUOUS" else ActionStatus.FAILED
        code = "COMFYUI_AMBIGUOUS" if status == ActionStatus.UNKNOWN else "COMFYUI_REJECTED"
        try:
            RECEIPT_STORE.append(
                _receipt(
                    actor_id=principal_id,
                    status=status,
                    resource=resource,
                    grant_id=request.grant_id,
                    task_id=request.task_id,
                    request_digest=digest,
                    external_reference=client_id,
                    error_code=code,
                    provenance="remote-outcome-uncertain" if status == ActionStatus.UNKNOWN else "server-observed-response",
                )
            )
        except Exception:
            pass
        if status == ActionStatus.UNKNOWN:
            raise HTTPException(status_code=502, detail="ComfyUI outcome is unknown; do not retry this grant") from exc
        raise HTTPException(status_code=502, detail="ComfyUI rejected the dispatch") from exc

    prompt_id = response.get("prompt_id") if isinstance(response, dict) else None
    if not prompt_id:
        RECEIPT_STORE.append(
            _receipt(
                actor_id=principal_id,
                status=ActionStatus.FAILED,
                resource=resource,
                grant_id=request.grant_id,
                task_id=request.task_id,
                request_digest=digest,
                external_reference=client_id,
                error_code="MISSING_PROMPT_ID",
                provenance="server-observed-response",
            )
        )
        raise HTTPException(status_code=502, detail="ComfyUI did not return prompt_id")

    spec = registry.get_spec(request.workflow_id)
    job = store_job(
        prompt_id,
        client_id,
        spec.media_type,
        workflow_id=spec.id,
        workflow_version=spec.version,
        parameters=request.parameters,
        owner_id=principal_id,
    )
    RECEIPT_STORE.append(
        _receipt(
            actor_id=principal_id,
            status=ActionStatus.SUCCEEDED,
            resource=resource,
            grant_id=request.grant_id,
            task_id=request.task_id,
            request_digest=digest,
            external_reference=prompt_id,
            provenance="server-observed-job-created",
        )
    )
    return job


@app.post("/api/v2/jobs", status_code=201)
async def dispatch_job_v2(
    request: CreateJobV2,
    principal_id: str = Depends(approver_subject),
):
    return await create_job_v2(request, principal_id)


@app.post("/api/jobs", dependencies=[Depends(authorize)])
async def create_job(request: CreateJob):
    raise HTTPException(
        status_code=410,
        detail={
            "code": "LEGACY_DISPATCH_DISABLED",
            "message": "Legacy arbitrary workflow dispatch is disabled. Use the consent-aware v2 API.",
        },
    )


@app.get("/api/jobs")
async def list_jobs(principal_id: str = Depends(authorize_actor)):
    return JOB_STORE.list_owned(principal_id, MAX_TRACKED_JOBS)


@app.get("/api/jobs/{job_id}")
async def get_job(job_id: str, principal_id: str = Depends(authorize_actor)):
    job = owned_job(job_id, principal_id)
    if job["status"] == "CANCELLED":
        return job

    history = await comfy_request("GET", f"/history/{job["prompt_id"]}")
    item = history.get(job["prompt_id"], {})
    status = item.get("status", {})
    messages = status.get("messages", []) if isinstance(status, dict) else []
    interrupted = any(
        isinstance(message, list)
        and len(message) >= 1
        and message[0] == "execution_interrupted"
        for message in messages
    )

    if status.get("completed"):
        outputs = item.get("outputs", {})
        files = []
        for node in outputs.values():
            if isinstance(node, dict):
                for key in ("images", "videos", "gifs"):
                    value = node.get(key, [])
                    if isinstance(value, list):
                        for output in value:
                            if not isinstance(output, dict):
                                continue
                            filename = str(output.get("filename", ""))
                            if not filename or PurePosixPath(filename).name != filename:
                                continue
                            files.append({
                                "filename": filename,
                                "subfolder": str(output.get("subfolder", "")),
                                "type": str(output.get("type", "output")),
                                "format": str(output.get("format", "")),
                                "media_index": len(files),
                                "media_path": "/api/jobs/%s/media/%d" % (job_id, len(files)),
                            })
        if interrupted:
            return JOB_STORE.update(
                job_id,
                status="CANCELLED",
                progress=0.0,
                error_code="JOB_CANCELLED",
                error_detail="ComfyUI reported an interrupted execution",
            )
        JOB_STORE.update(job_id, status="COMPLETED", progress=1.0, outputs=files)
    elif status.get("status_str") == "error":
        if interrupted:
            JOB_STORE.update(
                job_id,
                status="CANCELLED",
                progress=0.0,
                error_code="JOB_CANCELLED",
                error_detail="ComfyUI reported an interrupted execution",
            )
        else:
            JOB_STORE.update(
                job_id,
                status="FAILED",
                error_code="COMFYUI_JOB_FAILED",
                error_detail="ComfyUI reported a job failure",
            )
    else:
        queue_position = None
        current_status = job.get("status", "QUEUED")
        try:
            queue = await comfy_request("GET", "/queue")
            running = [
                str(item[1]) for item in queue.get("queue_running", [])
                if isinstance(item, list) and len(item) > 1
            ]
            pending = [
                str(item[1]) for item in queue.get("queue_pending", [])
                if isinstance(item, list) and len(item) > 1
            ]
            if job["prompt_id"] in running:
                current_status = "RUNNING"
                queue_position = 0
            elif job["prompt_id"] in pending:
                current_status = "QUEUED"
                queue_position = pending.index(job["prompt_id"]) + 1
        except HTTPException:
            pass
        updated = JOB_STORE.update(
            job_id,
            status=current_status,
            progress=float(job.get("progress", 0.0)),
        )
        updated["queue_position"] = queue_position
        return updated

    updated = JOB_STORE.get(job_id) or job
    updated["queue_position"] = 0 if updated["status"] == "COMPLETED" else None
    return updated

@app.get("/api/jobs/{job_id}/result")
async def get_result(job_id: str, principal_id: str = Depends(authorize_actor)):
    owned_job(job_id, principal_id)
    job = await get_job(job_id, principal_id)
    if job["status"] != "COMPLETED":
        raise HTTPException(status_code=409, detail="Job is not complete")
    return {"id": job_id, "outputs": job.get("outputs", [])}


@app.get("/api/jobs/{job_id}/media/{media_index}")
async def get_media(job_id: str, media_index: int, principal_id: str = Depends(authorize_actor)):
    job = owned_job(job_id, principal_id)
    outputs = job.get("outputs", [])
    if media_index < 0 or media_index >= len(outputs):
        raise HTTPException(status_code=404, detail="Media not found")
    output = outputs[media_index]
    if not isinstance(output, dict):
        raise HTTPException(status_code=404, detail="Media not found")
    filename = str(output.get("filename", ""))
    subfolder = str(output.get("subfolder", ""))
    media_type = str(output.get("type", "output"))
    if not filename or PurePosixPath(filename).name != filename:
        raise HTTPException(status_code=404, detail="Media not found")
    if PurePosixPath(subfolder).is_absolute() or ".." in PurePosixPath(subfolder).parts:
        raise HTTPException(status_code=404, detail="Media not found")
    if media_type not in {"output", "temp"}:
        raise HTTPException(status_code=404, detail="Media not found")

    async def stream():
        timeout = httpx.Timeout(60.0, connect=5.0)
        async with httpx.AsyncClient(timeout=timeout) as client:
            async with client.stream("GET", f"{COMFYUI}/view", params={"filename": filename, "subfolder": subfolder, "type": media_type}) as response:
                if response.status_code >= 400:
                    raise RuntimeError("media upstream rejected")
                content_length = response.headers.get("content-length")
                if content_length and int(content_length) > MEDIA_MAX_BYTES:
                    raise RuntimeError("media exceeds configured size limit")
                total = 0
                async for chunk in response.aiter_bytes(1024 * 64):
                    total += len(chunk)
                    if total > MEDIA_MAX_BYTES:
                        raise RuntimeError("media exceeds configured size limit")
                    yield chunk

    suffix = PurePosixPath(filename).suffix.lower()
    content_type = {".jpg":"image/jpeg",".jpeg":"image/jpeg",".png":"image/png",".webp":"image/webp",".gif":"image/gif",".mp4":"video/mp4",".webm":"video/webm",".mov":"video/quicktime"}.get(suffix,"application/octet-stream")
    return StreamingResponse(stream(), media_type=content_type, headers={"Content-Disposition": "inline; filename=\"%s\"" % filename, "Cache-Control": "private, no-store", "X-Content-Type-Options": "nosniff"})


@app.get("/api/v2/receipts")
async def list_receipts(
    task_id: str | None = None,
    principal_id: str = Depends(authorize_actor),
):
    return {"receipts": RECEIPT_STORE.list_owned(principal_id, task_id=task_id)}

app.include_router(make_job_router(authorize_actor, owned_job, comfy_request, JOB_STORE))
