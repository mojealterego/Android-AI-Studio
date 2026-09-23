import asyncio

from app.job_store import JobStore
from app.job_control import make_job_router


def _job(tmp_path):
    store = JobStore(tmp_path / "jobs.sqlite3")
    job = {
        "id": "job-1",
        "prompt_id": "prompt-1",
        "client_id": "client-1",
        "owner_id": "owner-1",
        "type": "IMAGE",
        "status": "QUEUED",
        "progress": 0.0,
        "parameters": {},
        "outputs": [],
    }
    store.create(job)
    return store, job


def test_cancel_pending_job_is_scoped_and_persisted(tmp_path):
    store, job = _job(tmp_path)
    calls = []

    async def comfy(method, path, **kwargs):
        calls.append((method, path, kwargs))
        if method == "GET" and path == "/queue":
            return {
                "queue_running": [],
                "queue_pending": [[0, "prompt-1", {}, {}, []]],
            }
        return {}

    router = make_job_router(
        lambda: "owner-1",
        lambda job_id, principal_id: store.get(job_id),
        comfy,
        store,
    )
    endpoint = next(
        route.endpoint for route in router.routes
        if getattr(route, "path", "") == "/api/jobs/{job_id}/cancel"
    )

    result = asyncio.run(endpoint("job-1", "owner-1"))

    assert result["cancelled"] is True
    assert result["action"] == "dequeue"
    assert store.get("job-1")["status"] == "CANCELLED"
    assert calls[1] == (
        "POST",
        "/queue",
        {"json": {"delete": ["prompt-1"]}},
    )


def test_cancel_running_job_uses_prompt_scoped_interrupt(tmp_path):
    store, job = _job(tmp_path)
    store.update("job-1", status="RUNNING")
    calls = []

    async def comfy(method, path, **kwargs):
        calls.append((method, path, kwargs))
        if method == "GET" and path == "/queue":
            return {
                "queue_running": [[0, "prompt-1", {}, {}, []]],
                "queue_pending": [],
            }
        return {}

    router = make_job_router(
        lambda: "owner-1",
        lambda job_id, principal_id: store.get(job_id),
        comfy,
        store,
    )
    endpoint = next(
        route.endpoint for route in router.routes
        if getattr(route, "path", "") == "/api/jobs/{job_id}/cancel"
    )

    result = asyncio.run(endpoint("job-1", "owner-1"))

    assert result["cancelled"] is True
    assert result["action"] == "interrupt"
    assert calls[1] == (
        "POST",
        "/interrupt",
        {"json": {"prompt_id": "prompt-1"}},
    )
    assert store.get("job-1")["status"] == "CANCELLED"


def test_cancel_never_uses_global_interrupt(tmp_path):
    store, job = _job(tmp_path)
    calls = []

    async def comfy(method, path, **kwargs):
        calls.append((method, path, kwargs))
        if method == "GET" and path == "/queue":
            return {"queue_running": [], "queue_pending": []}
        return {}

    router = make_job_router(
        lambda: "owner-1",
        lambda job_id, principal_id: store.get(job_id),
        comfy,
        store,
    )
    endpoint = next(
        route.endpoint for route in router.routes
        if getattr(route, "path", "") == "/api/jobs/{job_id}/cancel"
    )

    result = asyncio.run(endpoint("job-1", "owner-1"))

    assert result["cancelled"] is False
    assert all(path != "/interrupt" for _, path, _ in calls)
