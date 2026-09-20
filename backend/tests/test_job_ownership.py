"""Regression tests for single-instance job ownership checks."""

import asyncio

import pytest
from fastapi import HTTPException

from app import main


def setup_function():
    main.jobs.clear()


def test_store_job_assigns_server_controlled_instance_owner():
    job = main.store_job("prompt-1", "client-supplied", "IMAGE")
    assert job["owner_id"] == main.INSTANCE_OWNER_ID
    assert job["client_id"] == "client-supplied"


def test_list_jobs_filters_out_records_without_matching_owner():
    main.jobs.update({
        "owned": {"id": "owned", "owner_id": main.INSTANCE_OWNER_ID},
        "foreign": {"id": "foreign", "owner_id": "different-instance"},
        "legacy": {"id": "legacy"},
    })
    result = asyncio.run(main.list_jobs(main.INSTANCE_OWNER_ID))
    assert [job["id"] for job in result] == ["owned"]


def test_foreign_and_missing_job_ids_return_same_404():
    main.jobs["foreign"] = {"id": "foreign", "owner_id": "different-instance"}
    for job_id in ("foreign", "missing"):
        with pytest.raises(HTTPException) as exc:
            main.owned_job(job_id, main.INSTANCE_OWNER_ID)
        assert exc.value.status_code == 404
        assert exc.value.detail == "Job not found"


def test_foreign_job_status_does_not_call_comfyui(monkeypatch):
    main.jobs["foreign"] = {
        "id": "foreign", "owner_id": "different-instance", "prompt_id": "secret-prompt"
    }
    calls = []

    async def fake_comfy_request(*args, **kwargs):
        calls.append((args, kwargs))
        return {}

    monkeypatch.setattr(main, "comfy_request", fake_comfy_request)
    with pytest.raises(HTTPException) as exc:
        asyncio.run(main.get_job("foreign", main.INSTANCE_OWNER_ID))
    assert exc.value.status_code == 404
    assert calls == []


def test_foreign_job_result_does_not_call_comfyui(monkeypatch):
    main.jobs["foreign"] = {
        "id": "foreign", "owner_id": "different-instance", "prompt_id": "secret-prompt"
    }
    calls = []

    async def fake_comfy_request(*args, **kwargs):
        calls.append((args, kwargs))
        return {}

    monkeypatch.setattr(main, "comfy_request", fake_comfy_request)
    with pytest.raises(HTTPException) as exc:
        asyncio.run(main.get_result("foreign", main.INSTANCE_OWNER_ID))
    assert exc.value.status_code == 404
    assert calls == []


def test_v2_dispatch_remains_fail_closed():
    request = main.CreateJobV2(workflow_id="test_workflow", parameters={})
    with pytest.raises(HTTPException) as exc:
        asyncio.run(main.create_job_v2(request))
    assert exc.value.status_code == 503
    assert exc.value.detail["code"] == "CONSENT_ENFORCEMENT_NOT_CONFIGURED"
