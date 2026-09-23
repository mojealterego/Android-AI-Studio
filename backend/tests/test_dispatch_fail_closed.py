"""Regression tests for the fail-closed dispatch boundary."""

import asyncio

import pytest
from fastapi import HTTPException

from app.main import CreateJob, CreateJobV2, create_job, create_job_v2


def test_v2_dispatch_requires_consent():
    request = CreateJobV2(
        workflow_id="test_workflow",
        parameters={},
        grant_id="missing",
        session_id="session-1",
        task_id="task-1",
    )
    with pytest.raises(HTTPException) as exc:
        asyncio.run(create_job_v2(request, "single-instance"))
    assert exc.value.status_code == 404
    assert exc.value.detail == "Workflow not found"


def test_legacy_arbitrary_graph_dispatch_is_gone():
    request = CreateJob(type="IMAGE", prompt="test", workflow={"1": {"class_type": "NoOp"}})
    with pytest.raises(HTTPException) as exc:
        asyncio.run(create_job(request))
    assert exc.value.status_code == 410
    assert exc.value.detail["code"] == "LEGACY_DISPATCH_DISABLED"
