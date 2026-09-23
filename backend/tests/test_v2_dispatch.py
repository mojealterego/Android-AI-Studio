import asyncio
import json
from datetime import datetime, timezone

import pytest

from app import main
from app.consent_binding import workflow_resource
from app.consent_core import Access, Duration, Grant
from app.consent_store import ConsentStore
from app.workflow_registry import WorkflowRegistry


def make_registry(tmp_path):
    graph = {"1": {"class_type": "CLIPTextEncode", "inputs": {"text": "placeholder"}}}
    (tmp_path / "template.json").write_text(json.dumps(graph), encoding="utf-8")
    manifest = {
        "id": "test-image",
        "version": 1,
        "label": "Test image",
        "media_type": "IMAGE",
        "template_file": "template.json",
        "parameters": {
            "prompt": {"type": "string", "required": True, "min_length": 1, "max_length": 100}
        },
        "mappings": {"prompt": "1.text"},
    }
    (tmp_path / "test.manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    registry = WorkflowRegistry(tmp_path)
    registry.reload()
    return registry


def test_v2_dispatch_requires_exact_consent(monkeypatch, tmp_path):
    store = ConsentStore(tmp_path / "consent.sqlite3")
    monkeypatch.setattr(main, "CONSENT_STORE", store)
    monkeypatch.setattr(main, "registry", make_registry(tmp_path / "registry"))
    main.jobs.clear()

    request = main.CreateJobV2(
        workflow_id="test-image",
        parameters={"prompt": "approved prompt"},
        grant_id="missing",
        session_id="session-1",
        task_id="task-1",
    )

    with pytest.raises(main.HTTPException) as exc:
        asyncio.run(main.create_job_v2(request, main.INSTANCE_OWNER_ID))

    assert exc.value.status_code == 403


def test_v2_dispatch_consumes_matching_grant_and_sends_server_graph(monkeypatch, tmp_path):
    store = ConsentStore(tmp_path / "consent.sqlite3")
    registry = make_registry(tmp_path / "registry")
    monkeypatch.setattr(main, "CONSENT_STORE", store)
    monkeypatch.setattr(main, "registry", registry)
    main.jobs.clear()

    parameters = {"prompt": "approved prompt"}
    grant = Grant(
        subject_id=main.INSTANCE_OWNER_ID,
        resource=workflow_resource("test-image", parameters),
        access=frozenset({Access.WRITE}),
        duration=Duration.ONCE,
        created_at=datetime.now(timezone.utc),
        session_id="session-1",
        task_id="task-1",
    )
    store.create("grant-1", grant)

    calls = []

    async def fake_comfy_request(method, path, **kwargs):
        calls.append((method, path, kwargs))
        return {"prompt_id": "comfy-123"}

    monkeypatch.setattr(main, "comfy_request", fake_comfy_request)

    request = main.CreateJobV2(
        workflow_id="test-image",
        parameters=parameters,
        grant_id="grant-1",
        session_id="session-1",
        task_id="task-1",
        client_id="client-1",
    )

    job = asyncio.run(main.create_job_v2(request, main.INSTANCE_OWNER_ID))

    assert job["prompt_id"] == "comfy-123"
    assert job["owner_id"] == main.INSTANCE_OWNER_ID
    assert calls[0][0:2] == ("POST", "/prompt")
    assert calls[0][2]["json"]["client_id"] == "client-1"
    assert calls[0][2]["json"]["prompt"]["1"]["inputs"]["text"] == "approved prompt"
    assert store.get("grant-1") is not None

    with pytest.raises(Exception):
        asyncio.run(main.create_job_v2(request, main.INSTANCE_OWNER_ID))
