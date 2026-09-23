from app.job_store import JobStore


def sample_job(job_id="job-1", owner_id="owner-1"):
    return {
        "id": job_id,
        "prompt_id": "prompt-1",
        "client_id": "client-1",
        "owner_id": owner_id,
        "type": "IMAGE",
        "status": "QUEUED",
        "progress": 0.0,
        "workflow_id": "image-basic",
        "workflow_version": 2,
        "parameters": {"prompt": "hello"},
        "outputs": [],
    }


def test_job_store_survives_reopen(tmp_path):
    path = tmp_path / "jobs.sqlite3"
    first = JobStore(path)
    first.create(sample_job())

    second = JobStore(path)
    restored = second.get("job-1")

    assert restored is not None
    assert restored["owner_id"] == "owner-1"
    assert restored["workflow_id"] == "image-basic"
    assert restored["workflow_version"] == 2
    assert restored["parameters"] == {"prompt": "hello"}


def test_job_store_updates_lifecycle_and_outputs(tmp_path):
    store = JobStore(tmp_path / "jobs.sqlite3")
    store.create(sample_job())

    updated = store.update(
        "job-1",
        status="COMPLETED",
        progress=1.0,
        outputs=[{"filename": "image.png", "type": "output"}],
    )

    assert updated["status"] == "COMPLETED"
    assert updated["progress"] == 1.0
    assert updated["outputs"][0]["filename"] == "image.png"


def test_job_store_enforces_owner_filter(tmp_path):
    store = JobStore(tmp_path / "jobs.sqlite3")
    store.create(sample_job("owned", "owner-1"))
    store.create(sample_job("foreign", "owner-2"))

    assert [x["id"] for x in store.list_owned("owner-1")] == ["owned"]
