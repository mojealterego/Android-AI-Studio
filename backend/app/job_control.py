from __future__ import annotations

from typing import Any, Awaitable, Callable

from fastapi import APIRouter, Depends, HTTPException


ComfyRequest = Callable[..., Awaitable[Any]]


def make_job_router(
    authorize_actor: Callable[..., Awaitable[str]],
    owned_job: Callable[[str, str], dict[str, Any]],
    comfy_request: ComfyRequest,
    job_store: Any,
) -> APIRouter:
    router = APIRouter()

    @router.post("/api/jobs/{job_id}/cancel")
    async def cancel_job(
        job_id: str,
        principal_id: str = Depends(authorize_actor),
    ):
        job = owned_job(job_id, principal_id)
        if job["status"] in {"COMPLETED", "FAILED", "CANCELLED", "UNKNOWN"}:
            return {"id": job_id, "cancelled": False, "status": job["status"]}

        prompt_id = str(job["prompt_id"])
        queue = await comfy_request("GET", "/queue")
        running = {
            str(item[1])
            for item in queue.get("queue_running", [])
            if isinstance(item, list) and len(item) > 1
        }
        pending = {
            str(item[1])
            for item in queue.get("queue_pending", [])
            if isinstance(item, list) and len(item) > 1
        }

        acted = False
        action = None

        if prompt_id in running:
            await comfy_request(
                "POST",
                "/interrupt",
                json={"prompt_id": prompt_id},
            )
            acted = True
            action = "interrupt"
        elif prompt_id in pending:
            await comfy_request(
                "POST",
                "/queue",
                json={"delete": [prompt_id]},
            )
            acted = True
            action = "dequeue"

            # Close the small pending->running race without ever issuing
            # an unscoped/global interrupt.
            refreshed = await comfy_request("GET", "/queue")
            refreshed_running = {
                str(item[1])
                for item in refreshed.get("queue_running", [])
                if isinstance(item, list) and len(item) > 1
            }
            if prompt_id in refreshed_running:
                await comfy_request(
                    "POST",
                    "/interrupt",
                    json={"prompt_id": prompt_id},
                )
                action = "dequeue+interrupt"
        else:
            # The job may have moved to history between our application
            # state and the ComfyUI snapshot. Treat this as an idempotent
            # no-op; the next GET will reconcile the final state.
            return {"id": job_id, "cancelled": False, "status": job["status"]}

        if acted:
            updated = job_store.update(
                job_id,
                status="CANCELLED",
                progress=0.0,
                error_code="JOB_CANCELLED",
                error_detail="Generation cancelled by the authenticated owner",
            )
            return {
                "id": job_id,
                "cancelled": True,
                "status": updated["status"],
                "action": action,
            }

        raise HTTPException(status_code=409, detail="Job could not be cancelled")

    return router
