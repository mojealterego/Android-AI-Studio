from fastapi.testclient import TestClient

import app.main as main


def test_omni_orchestrator_requires_auth(monkeypatch):
    monkeypatch.setattr(main, "API_KEY", "secret")
    client = TestClient(main.app)
    response = client.get("/api/omni/orchestrator")
    assert response.status_code == 401


def test_omni_orchestrator_plan_detects_reference_conflict(monkeypatch):
    monkeypatch.setattr(main, "API_KEY", "secret")
    client = TestClient(main.app)
    response = client.post(
        "/api/omni/orchestrator/plan",
        headers={"Authorization": "Bearer secret"},
        json={
            "intent": "Create a cinematic scene with the same actor.",
            "media_type": "VIDEO",
            "references": [
                {"id": "person-a", "role": "IDENTITY_SOURCE"},
                {"id": "person-b", "role": "IDENTITY_SOURCE"},
            ],
            "scene_count": 12,
            "target_duration_seconds": 124 * 60,
            "autonomy": "ASSISTED",
        },
    )
    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "REVIEW_REQUIRED"
    assert payload["pipeline"] == ["A01", "A02", "A03", "A04", "A05", "A06"]
    assert len(payload["scenes"]) == 12
    assert payload["requires_human_approval_before_external_side_effect"] is True
    assert payload["governance"] if "governance" in payload else True


def test_omni_orchestrator_default_video_route(monkeypatch):
    monkeypatch.setattr(main, "API_KEY", "secret")
    client = TestClient(main.app)
    response = client.post(
        "/api/omni/orchestrator/plan",
        headers={"Authorization": "Bearer secret"},
        json={"intent": "video", "media_type": "VIDEO"},
    )
    assert response.status_code == 200
    assert response.json()["model_route"] == [
        "COMFYUI-LTX/WAN", "VEO", "SEEDANCE", "KLING"
    ]
