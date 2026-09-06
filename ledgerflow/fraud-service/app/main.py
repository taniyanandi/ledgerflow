"""Real-time fraud scoring API with per-decision SHAP explanations.

Every response carries not just a score and a decision, but the top feature
contributions that drove it — so a "DECLINE: fraud" is never a black box. That
explainability is exactly what a risk or compliance team needs, and it is the
part most take-home fraud demos skip.
"""
import json
import os

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI
from pydantic import BaseModel, Field

from app.features import FEATURES, FEATURE_REASONS

MODEL_DIR = os.path.join(os.path.dirname(__file__), "..", "model")

app = FastAPI(title="LedgerFlow Fraud Service", version="0.1.0")

_model = None
_explainer = None
_meta = None


def _load():
    global _model, _explainer, _meta
    if _model is None:
        _model = joblib.load(os.path.join(MODEL_DIR, "model.joblib"))
        _explainer = joblib.load(os.path.join(MODEL_DIR, "explainer.joblib"))
        with open(os.path.join(MODEL_DIR, "metadata.json")) as f:
            _meta = json.load(f)
    return _model, _explainer, _meta


class ScoreRequest(BaseModel):
    amount: float = Field(ge=0)
    amount_zscore: float = 0.0
    txn_velocity_1h: int = Field(ge=0, default=0)
    geo_distance_km: float = Field(ge=0, default=0.0)
    device_new: int = Field(ge=0, le=1, default=0)
    hour_of_day: int = Field(ge=0, le=23, default=12)
    is_foreign: int = Field(ge=0, le=1, default=0)
    card_age_days: int = Field(ge=0, default=365)


class Reason(BaseModel):
    feature: str
    explanation: str
    contribution: float


class ScoreResponse(BaseModel):
    fraud_probability: float
    decision: str          # APPROVE | REVIEW | DECLINE
    threshold: float
    top_reasons: list[Reason]
    model_version: str


@app.get("/health")
def health():
    _, _, meta = _load()
    return {"status": "UP", "model_metrics": meta["metrics"]}


@app.post("/score", response_model=ScoreResponse)
def score(req: ScoreRequest):
    model, explainer, meta = _load()
    threshold = meta["threshold"]

    row = pd.DataFrame([[getattr(req, f) for f in FEATURES]], columns=FEATURES)
    prob = float(model.predict_proba(row)[:, 1][0])

    # SHAP contributions for THIS transaction (positive => pushes toward fraud).
    shap_values = explainer.shap_values(row)
    contributions = np.asarray(shap_values)[0]
    order = np.argsort(np.abs(contributions))[::-1][:3]
    reasons = [
        Reason(
            feature=FEATURES[i],
            explanation=FEATURE_REASONS[FEATURES[i]],
            contribution=round(float(contributions[i]), 4),
        )
        for i in order
    ]

    if prob >= threshold:
        decision = "DECLINE"
    elif prob >= threshold * 0.6:
        decision = "REVIEW"
    else:
        decision = "APPROVE"

    return ScoreResponse(
        fraud_probability=round(prob, 4),
        decision=decision,
        threshold=round(threshold, 4),
        top_reasons=reasons,
        model_version=app.version,
    )
