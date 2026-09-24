from fastapi.testclient import TestClient

from app.main import app


def test_runtime_load_all_requires_three_slots(monkeypatch):
    monkeypatch.setattr("app.omni_runtime.API_KEY", "test-key")
    client = TestClient(app)
    payload = {
        "slots": [
            {"model_path": "/models/chat.gguf", "model_name": "chat.gguf"},
            {"model_path": "/models/image.gguf", "model_name": "image.gguf"},
        ]
    }
    response = client.post(
        "/api/omni/runtime/load-all",
        headers={"Authorization": "Bearer test-key"},
        json=payload,
    )
    assert response.status_code == 422


def test_runtime_load_all_registers_all_three_slots(monkeypatch):
    monkeypatch.setattr("app.omni_runtime.API_KEY", "test-key")
    client = TestClient(app)
    payload = {
        "slots": [
            {"model_path": "/models/chat.gguf", "model_name": "chat.gguf"},
            {"model_path": "/models/image.gguf", "model_name": "image.gguf"},
            {"model_path": "/models/video.gguf", "model_name": "video.gguf"},
        ]
    }
    response = client.post(
        "/api/omni/runtime/load-all",
        headers={"Authorization": "Bearer test-key"},
        json=payload,
    )
    assert response.status_code == 200
    body = response.json()
    assert body["simultaneous_resident_slots"] == 3
    assert len(body["slots"]) == 3
    assert all(item["state"]["loaded"] for item in body["slots"])
