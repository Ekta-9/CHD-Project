"""Test fixtures for the ML service.

The real service loads a HuggingFace transformer model at import time, which
is far too heavy (and network-dependent) for unit tests. Before `main` is
imported we install lightweight fake `torch` and `transformers` modules into
sys.modules. The fakes implement exactly the surface main.py touches, and let
each test steer the "model" output by mutating `main.model.logits_values`.
"""

import contextlib
import math
import sys
import types
from pathlib import Path
from types import SimpleNamespace

import pytest

# Make `import main` resolve to ml-service/main.py
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


# ---------------------------------------------------------------------------
# Minimal tensor stand-ins (only what main.py actually calls)
# ---------------------------------------------------------------------------

class _Scalar:
    def __init__(self, value):
        self._value = value

    def item(self):
        return self._value


class _Vector:
    def __init__(self, values):
        self.values = list(values)

    def __getitem__(self, idx):
        return _Scalar(self.values[idx])

    def __len__(self):
        return len(self.values)

    @property
    def shape(self):
        return (len(self.values),)


class _Logits:
    def __init__(self, values):
        self.values = list(values)

    def argmax(self, _dim=-1):
        best = max(range(len(self.values)), key=lambda i: self.values[i])
        return _Scalar(best)

    @property
    def shape(self):
        return (1, len(self.values))


class _ProbMatrix:
    """Result of torch.softmax(logits, dim=-1); only row [0] is ever read."""

    def __init__(self, vector):
        self._vector = vector

    def __getitem__(self, _idx):
        return self._vector


def _softmax(values):
    peak = max(values)
    exps = [math.exp(v - peak) for v in values]
    total = sum(exps)
    return [e / total for e in exps]


# ---------------------------------------------------------------------------
# Fake torch
# ---------------------------------------------------------------------------

def _make_fake_torch():
    torch_mod = types.ModuleType("torch")
    torch_mod.no_grad = contextlib.nullcontext
    torch_mod.softmax = lambda logits, dim=-1: _ProbMatrix(_Vector(_softmax(logits.values)))

    def topk(vector, k):
        order = sorted(range(len(vector.values)), key=lambda i: vector.values[i], reverse=True)[:k]
        return _Vector([vector.values[i] for i in order]), _Vector(order)

    torch_mod.topk = topk
    return torch_mod


# ---------------------------------------------------------------------------
# Fake transformers
# ---------------------------------------------------------------------------

class FakeProcessor:
    @classmethod
    def from_pretrained(cls, _name, **_kwargs):
        return cls()

    def __call__(self, images=None, return_tensors=None):
        return {}


class FakeModel:
    def __init__(self):
        self.config = SimpleNamespace(
            id2label={0: "Normal", 1: "ASD", 2: "VSD"},
            num_labels=3,
        )
        # Default output: "Normal" wins
        self.logits_values = [4.0, 1.0, 1.0]

    @classmethod
    def from_pretrained(cls, _name, **_kwargs):
        return _FAKE_MODEL

    def eval(self):
        return self

    def __call__(self, **_inputs):
        return SimpleNamespace(logits=_Logits(self.logits_values))


_FAKE_MODEL = FakeModel()


def _make_fake_transformers():
    transformers_mod = types.ModuleType("transformers")
    transformers_mod.AutoImageProcessor = FakeProcessor
    transformers_mod.AutoModelForImageClassification = FakeModel
    transformers_mod.ViTImageProcessor = FakeProcessor
    transformers_mod.ViTForImageClassification = FakeModel
    transformers_mod.ConvNextImageProcessor = FakeProcessor
    transformers_mod.ConvNextForImageClassification = FakeModel
    return transformers_mod


sys.modules["torch"] = _make_fake_torch()
sys.modules["transformers"] = _make_fake_transformers()


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def app_main():
    import main
    return main


@pytest.fixture()
def client(app_main):
    from fastapi.testclient import TestClient

    # Reset the fake model to defaults before every test
    app_main.model.logits_values = [4.0, 1.0, 1.0]
    return TestClient(app_main.app)
