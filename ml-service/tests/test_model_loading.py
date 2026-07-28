"""Tests for main.py's import-time model loading logic.

main.py resolves the model path and loads the model when the module is
imported, so these tests reload the module under controlled conditions
(env vars, broken loaders) and always restore the pristine module afterwards.
"""

import importlib
from types import SimpleNamespace

import conftest


def _reload_main():
    import main
    return importlib.reload(main)


def test_dot_slash_relative_path_missing_falls_back_to_default(monkeypatch):
    monkeypatch.setenv("MODEL_PATH", "./models/does-not-exist")
    try:
        m = _reload_main()
        assert m.MODEL_NAME == "google/vit-base-patch16-224-in21k"
        assert m.model is not None
    finally:
        monkeypatch.undo()
        _reload_main()


def test_bare_relative_path_missing_falls_back_to_default(monkeypatch):
    monkeypatch.setenv("MODEL_PATH", "some-model-dir/nested")
    try:
        m = _reload_main()
        assert m.MODEL_NAME == "google/vit-base-patch16-224-in21k"
    finally:
        monkeypatch.undo()
        _reload_main()


def test_existing_local_dir_without_weights_still_attempts_load(monkeypatch, tmp_path):
    # Directory exists but has no config/weights: main should warn and try anyway
    monkeypatch.setenv("MODEL_PATH", str(tmp_path))
    try:
        m = _reload_main()
        assert m.MODEL_NAME == str(tmp_path)
        assert m.model is not None  # fake loader succeeds regardless
    finally:
        monkeypatch.undo()
        _reload_main()


def test_existing_local_dir_with_weights_detected(monkeypatch, tmp_path):
    (tmp_path / "config.json").write_text("{}")
    (tmp_path / "preprocessor_config.json").write_text("{}")
    (tmp_path / "model.safetensors").write_bytes(b"\0")
    monkeypatch.setenv("MODEL_PATH", str(tmp_path))
    try:
        m = _reload_main()
        assert m.model is not None
    finally:
        monkeypatch.undo()
        _reload_main()


def test_all_loaders_failing_leaves_model_unloaded(monkeypatch):
    def explode(_name, **_kwargs):
        raise RuntimeError("no such model")

    monkeypatch.setattr(conftest.FakeProcessor, "from_pretrained", explode)
    monkeypatch.setattr(conftest.FakeModel, "from_pretrained", explode)
    try:
        m = _reload_main()
        assert m.model is None
        assert m.processor is None

        from fastapi.testclient import TestClient
        health = TestClient(m.app).get("/health").json()
        assert health["model_loaded"] is False
        assert health["classes"] == []
    finally:
        monkeypatch.undo()
        _reload_main()


def test_model_without_label_config_uses_class_labels_env(monkeypatch):
    original_config = conftest._FAKE_MODEL.config
    conftest._FAKE_MODEL.config = SimpleNamespace(num_labels=2)
    monkeypatch.setenv("CLASS_LABELS", "ASD,VSD")
    try:
        m = _reload_main()
        assert m.id2label == {0: "ASD", 1: "VSD"}
        assert m.label2id == {"ASD": 0, "VSD": 1}
    finally:
        conftest._FAKE_MODEL.config = original_config
        monkeypatch.undo()
        _reload_main()
