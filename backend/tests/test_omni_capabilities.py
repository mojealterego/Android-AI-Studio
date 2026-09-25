from fastapi.testclient import TestClient

import app.main as main
import app.omni_capabilities as capabilities


def test_capability_registry_requires_auth(monkeypatch):
    monkeypatch.setattr(main, "API_KEY", "secret")
    monkeypatch.setattr(capabilities, "API_KEY", "secret")
    response = TestClient(main.app).get("/api/omni/capabilities")
    assert response.status_code == 401


def test_capability_registry_contains_voice_music_and_publish(monkeypatch):
    monkeypatch.setattr(main, "API_KEY", "secret")
    monkeypatch.setattr(capabilities, "API_KEY", "secret")
    response = TestClient(main.app).get(
        "/api/omni/capabilities",
        headers={"Authorization": "Bearer secret"},
    )
    assert response.status_code == 200
    items = {item["id"]: item for item in response.json()["capabilities"]}
    assert {"voice", "music", "publish"}.issubset(items)
    assert items["publish"]["supports_external_publish"] is True
