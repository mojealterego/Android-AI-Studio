import asyncio

import pytest
from fastapi import HTTPException

from app import main


def _job(owner_id):
    return {
        "id": "job-1",
        "prompt_id": "prompt-1",
        "client_id": "client-1",
        "owner_id": owner_id,
        "type": "IMAGE",
        "status": "COMPLETED",
        "progress": 1.0,
        "parameters": {},
        "outputs": [
            {
                "filename": "result.png",
                "subfolder": "session",
                "type": "output",
                "format": "image/png",
                "media_index": 0,
                "media_path": "/api/jobs/job-1/media/0",
            }
        ],
    }


def test_media_proxy_enforces_job_ownership(monkeypatch, tmp_path):
    from app.job_store import JobStore

    store = JobStore(tmp_path / "jobs.sqlite3")
    store.create(_job("different-instance"))
    monkeypatch.setattr(main, "JOB_STORE", store)

    with pytest.raises(HTTPException) as exc:
        asyncio.run(main.get_media("job-1", 0, main.INSTANCE_OWNER_ID))

    assert exc.value.status_code == 404


def test_media_proxy_rejects_invalid_output_index(monkeypatch, tmp_path):
    from app.job_store import JobStore

    store = JobStore(tmp_path / "jobs.sqlite3")
    job = _job(main.INSTANCE_OWNER_ID)
    store.create(job)
    monkeypatch.setattr(main, "JOB_STORE", store)

    with pytest.raises(HTTPException) as exc:
        asyncio.run(main.get_media("job-1", 1, main.INSTANCE_OWNER_ID))

    assert exc.value.status_code == 404


def test_media_proxy_streams_only_the_stored_output(monkeypatch, tmp_path):
    from app.job_store import JobStore

    store = JobStore(tmp_path / "jobs.sqlite3")
    store.create(_job(main.INSTANCE_OWNER_ID))
    monkeypatch.setattr(main, "JOB_STORE", store)

    calls = []

    class FakeResponse:
        status_code = 200
        headers = {"content-length": "11"}

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def aiter_bytes(self, size):
            yield b"hello "
            yield b"world"

    class FakeClient:
        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        def stream(self, method, url, params):
            calls.append((method, url, params))
            return FakeResponse()

    monkeypatch.setattr(main.httpx, "AsyncClient", lambda **kwargs: FakeClient())

    response = asyncio.run(main.get_media("job-1", 0, main.INSTANCE_OWNER_ID))
    body = b""

    async def consume():
        nonlocal body
        async for chunk in response.body_iterator:
            body += chunk

    asyncio.run(consume())

    assert body == b"hello world"
    assert calls == [(
        "GET",
        f"{main.COMFYUI}/view",
        {"filename": "result.png", "subfolder": "session", "type": "output"},
    )]
    assert response.headers["content-type"] == "image/png"
    assert response.headers["cache-control"] == "private, no-store"
    assert response.headers["x-content-type-options"] == "nosniff"


def test_media_proxy_rejects_path_traversal_in_persisted_metadata(monkeypatch, tmp_path):
    from app.job_store import JobStore

    job = _job(main.INSTANCE_OWNER_ID)
    job["outputs"][0]["subfolder"] = "../secret"
    store = JobStore(tmp_path / "jobs.sqlite3")
    store.create(job)
    monkeypatch.setattr(main, "JOB_STORE", store)

    with pytest.raises(HTTPException) as exc:
        asyncio.run(main.get_media("job-1", 0, main.INSTANCE_OWNER_ID))

    assert exc.value.status_code == 404
