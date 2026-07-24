"""Tests for the ML service FastAPI endpoints (model mocked, see conftest.py)."""

import base64
import io

from PIL import Image


def png_b64(mode="RGB", size=(8, 8), color=None):
    if color is None:
        color = (200, 30, 30) if mode == "RGB" else 128
    img = Image.new(mode, size, color)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode()


# ---------------------------------------------------------------------------
# Basic endpoints
# ---------------------------------------------------------------------------

def test_root_returns_greeting(client):
    response = client.get("/")
    assert response.status_code == 200
    assert "ML Service" in response.json()["message"]


def test_health_reports_model_loaded(client):
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "healthy"
    assert body["model_loaded"] is True
    assert body["classes"] == ["Normal", "ASD", "VSD"]


def test_test_endpoint_echoes_request_shape(client):
    response = client.post("/test", json={"scan_id": "abc-123", "image_data": png_b64()})
    assert response.status_code == 200
    body = response.json()
    assert body["scan_id"] == "abc-123"
    assert body["has_image_data"] is True


def test_test_endpoint_without_image(client):
    response = client.post("/test", json={"scan_id": "abc-123"})
    assert response.status_code == 200
    assert response.json()["has_image_data"] is False


# ---------------------------------------------------------------------------
# /predict success paths
# ---------------------------------------------------------------------------

def test_predict_returns_full_prediction(client):
    response = client.post("/predict", json={"scan_id": "scan-1", "image_data": png_b64()})
    assert response.status_code == 200
    body = response.json()
    assert body["scan_id"] == "scan-1"
    assert body["prediction"] == "Normal"
    assert body["status"] == "COMPLETED"
    assert 0.0 <= body["confidence_score"] <= 1.0
    assert set(body["class_probabilities"].keys()) == {"Normal", "ASD", "VSD"}
    # Probabilities are a softmax: they must sum to ~1
    assert abs(sum(body["class_probabilities"].values()) - 1.0) < 0.01


def test_predict_follows_model_output(client, app_main):
    app_main.model.logits_values = [0.5, 6.0, 0.5]  # ASD wins

    response = client.post("/predict", json={"scan_id": "scan-2", "image_data": png_b64()})
    assert response.status_code == 200
    body = response.json()
    assert body["prediction"] == "ASD"
    assert body["confidence_score"] == max(body["class_probabilities"].values())


def test_predict_converts_grayscale_to_rgb(client):
    response = client.post("/predict", json={"scan_id": "gray", "image_data": png_b64(mode="L")})
    assert response.status_code == 200
    assert response.json()["status"] == "COMPLETED"


def test_predict_supports_legacy_mri_scan_id(client):
    response = client.post("/predict", json={"mri_scan_id": 42, "image_data": png_b64()})
    assert response.status_code == 200
    assert response.json()["scan_id"] == "42"


# ---------------------------------------------------------------------------
# /predict error handling
# ---------------------------------------------------------------------------

def test_predict_rejects_invalid_base64(client):
    response = client.post("/predict", json={"scan_id": "bad", "image_data": "%%%not-base64%%%"})
    assert response.status_code == 400
    assert "Failed to decode image data" in response.json()["detail"]


def test_predict_rejects_non_image_bytes(client):
    not_an_image = base64.b64encode(b"this is just text").decode()
    response = client.post("/predict", json={"scan_id": "bad", "image_data": not_an_image})
    assert response.status_code == 400
    assert "Failed to decode image data" in response.json()["detail"]


def test_predict_without_image_and_no_fallback_file(client):
    response = client.post("/predict", json={"scan_id": "none"})
    assert response.status_code == 400


def test_predict_fails_cleanly_when_model_not_loaded(client, app_main, monkeypatch):
    monkeypatch.setattr(app_main, "model", None)
    response = client.post("/predict", json={"scan_id": "x", "image_data": png_b64()})
    assert response.status_code == 500
    assert "not loaded" in response.json()["detail"]


def test_predict_fails_cleanly_when_labels_missing(client, app_main, monkeypatch):
    monkeypatch.setattr(app_main, "id2label", {})
    response = client.post("/predict", json={"scan_id": "x", "image_data": png_b64()})
    assert response.status_code == 500
    assert "class labels" in response.json()["detail"]


def test_predict_validation_error_returns_422(client):
    response = client.post("/predict", json={"image_data": {"unexpected": "object"}})
    assert response.status_code == 422
    assert "Validation error" in response.json()["detail"]
