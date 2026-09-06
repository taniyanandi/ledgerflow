"""Train the fraud model and persist everything the API needs.

Deliberate modelling choices, each worth explaining in an interview:
  * Class imbalance is handled with scale_pos_weight rather than naive resampling,
    which keeps the probability calibration usable.
  * The decision threshold is TUNED, not left at 0.5 — we pick the point that hits
    a target precision, because a fraud model that blocks good customers is worse
    than useless. The chosen threshold is saved alongside the model.
  * A SHAP TreeExplainer is fit once and saved, so every prediction can say WHY.

Run:  python -m app.train
"""
import json
import os

import joblib
import numpy as np
import shap
from sklearn.metrics import (average_precision_score, precision_recall_curve,
                             precision_score, recall_score, roc_auc_score)
from sklearn.model_selection import train_test_split
from xgboost import XGBClassifier

from app.data import generate
from app.features import FEATURES

MODEL_DIR = os.path.join(os.path.dirname(__file__), "..", "model")
TARGET_PRECISION = 0.90


def choose_threshold(y_true, y_prob, target_precision: float) -> float:
    """Lowest threshold that still achieves the target precision (max recall for it)."""
    precisions, recalls, thresholds = precision_recall_curve(y_true, y_prob)
    best = 0.5
    for p, t in zip(precisions[:-1], thresholds):
        if p >= target_precision:
            best = float(t)
            break
    return best


def main():
    df = generate()
    X = df[FEATURES]
    y = df["is_fraud"]

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.25, stratify=y, random_state=42)

    pos_weight = float((y_train == 0).sum() / max((y_train == 1).sum(), 1))

    model = XGBClassifier(
        n_estimators=300,
        max_depth=5,
        learning_rate=0.08,
        subsample=0.9,
        colsample_bytree=0.9,
        scale_pos_weight=pos_weight,
        eval_metric="aucpr",
        n_jobs=4,
        random_state=42,
    )
    model.fit(X_train, y_train)

    y_prob = model.predict_proba(X_test)[:, 1]
    threshold = choose_threshold(y_test, y_prob, TARGET_PRECISION)
    y_pred = (y_prob >= threshold).astype(int)

    metrics = {
        "roc_auc": round(float(roc_auc_score(y_test, y_prob)), 4),
        "pr_auc": round(float(average_precision_score(y_test, y_prob)), 4),
        "precision_at_threshold": round(float(precision_score(y_test, y_pred)), 4),
        "recall_at_threshold": round(float(recall_score(y_test, y_pred)), 4),
        "threshold": round(threshold, 4),
        "n_train": int(len(X_train)),
        "n_test": int(len(X_test)),
        "fraud_rate": round(float(y.mean()), 4),
    }

    explainer = shap.TreeExplainer(model)

    os.makedirs(MODEL_DIR, exist_ok=True)
    joblib.dump(model, os.path.join(MODEL_DIR, "model.joblib"))
    joblib.dump(explainer, os.path.join(MODEL_DIR, "explainer.joblib"))
    with open(os.path.join(MODEL_DIR, "metadata.json"), "w") as f:
        json.dump({"features": FEATURES, "threshold": threshold, "metrics": metrics}, f, indent=2)

    print(json.dumps(metrics, indent=2))
    print("Saved model, explainer and metadata to", os.path.abspath(MODEL_DIR))


if __name__ == "__main__":
    main()
