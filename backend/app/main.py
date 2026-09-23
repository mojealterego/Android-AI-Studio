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
from .consent_core import ConsentError, Duration, Access, Grant
from .consent_binding import workflow_resource, action_digest
from .consent_receipt import ActionReceipt, ActionStatus
from .receipt_store import ReceiptStore
from .approval_auth import ApprovalAuthenticationError, authenticate_approver
from .job_store import JobStore

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
    except httpx.HTTPError as exc:
        raise HTTPException(
            status_code=502,
            detail=f"ComfyUI request failed ({exc.__class__.__name__})",
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
        occurred_at=__import__("datetime").datetime.now(__import__("datetime").timezone.utc),
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
    from datetime import datetime, timedelta, timezone
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
        try:
            RECEIPT_STORE.append(
                _receipt(
                    actor_id=principal_id,
                    status=ActionStatus.UNKNOWN,
                    resource=resource,
                    grant_id=request.grant_id,
                    task_id=request.task_id,
                    request_digest=digest,
                    external_reference=client_id,
                    error_code="COMFYUI_AMBIGUOUS",
                    provenance="remote-outcome-uncertain",
                )
            )
        except Exception:
            pass
        raise HTTPException(status_code=502, detail="ComfyUI outcome is unknown; do not retry this grant") from exc

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
    history = await comfy_request("GET", f"/history/{job['prompt_id']}")
    item = history.get(job["prompt_id"], {})
    status = item.get("status", {})
    if status.get("completed"):
        outputs = item.get("outputs", {})
        files = []
        for node in outputs.values():
            if isinstance(node, dict):
                for key in ("images", "videos", "gifs"):
                    value = node.get(key, [])
                    if isinstance(value, list):
                        files.extend(value)
        JOB_STORE.update(job_id, status="COMPLETED", progress=1.0, outputs=files)
    elif status.get("status_str") == "error":
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
async def get_result(job_id: str, principal_id: str = Depends(authorize_actor)):
    owned_job(job_id, principal_id)
    job = await get_job(job_id, principal_id)
    if job["status"] != "COMPLETED":
        raise HTTPException(status_code=409, detail="Job is not complete")
    return {"id": job_id, "outputs": job.get("outputs", [])}


@app.get("/api/v2/receipts")
async def list_receipts(
    task_id: str | None = None,
    principal_id: str = Depends(authorize_actor),
):
    return {"receipts": RECEIPT_STORE.list_owned(principal_id, task_id=task_id)}
