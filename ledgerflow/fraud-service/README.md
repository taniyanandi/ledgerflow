# LedgerFlow Fraud Service (Phase 4)

Real-time transaction fraud scoring with **per-decision SHAP explanations**, served
over HTTP and consumed by the payment orchestrator's risk-check saga step.

## What it does

`POST /score` takes engineered transaction features and returns a fraud
probability, a decision (`APPROVE` / `REVIEW` / `DECLINE`), the tuned threshold,
and the **top feature contributions that drove the score** — so every decline
comes with a reason a human can read.

```jsonc
// request
{ "amount": 1800, "amount_zscore": 3.5, "txn_velocity_1h": 8,
  "geo_distance_km": 900, "device_new": 1, "hour_of_day": 3,
  "is_foreign": 1, "card_age_days": 15 }

// response
{ "fraud_probability": 0.9995, "decision": "DECLINE", "threshold": 0.8751,
  "top_reasons": [
    { "feature": "card_age_days", "explanation": "card age", "contribution": 5.04 },
    { "feature": "hour_of_day", "explanation": "unusual time of day", "contribution": 1.97 }
  ], "model_version": "0.1.0" }
```

## Model

- **XGBoost** gradient-boosted trees on eight engineered payment features
  (amount z-score per user, 1-hour velocity, geo-distance from last transaction,
  device novelty, cross-border flag, hour of day, card age).
- **Class imbalance** handled with `scale_pos_weight` (fraud ≈ 1.6%).
- **Threshold is tuned**, not left at 0.5: we pick the operating point that meets a
  target precision (90%) and maximises recall there, then persist it with the model.
- **SHAP `TreeExplainer`** is fit once and saved, giving fast per-request explanations.

Held-out metrics from the shipped training run:

| Metric | Value |
|---|---|
| ROC-AUC | 0.917 |
| PR-AUC | 0.836 |
| Precision @ threshold | 0.90 |
| Recall @ threshold | 0.83 |

> The training data is **synthetically generated with a documented, overlapping
> fraud signal** (see `app/data.py`) because the public fraud datasets need a login.
> The overlap is intentional — a model scoring near-perfect on this task would be a
> warning sign. To use a real dataset, replace `data.py` only.

## Run

```bash
pip install -r requirements.txt
python -m app.train        # writes model/, explainer, metadata.json
uvicorn app.main:app --reload
# docs at http://localhost:8000/docs
```

Or via the top-level `docker compose up` — this service is wired into the platform
and the Java orchestrator calls it at `http://fraud-service:8000/score`.
