"""Generate a synthetic-but-realistic payments dataset.

Public fraud datasets (IEEE-CIS, the Kaggle credit-card set) require a login to
download, so this project ships a generator instead: the data is reproducible,
the fraud signal is documented, and anyone can retrain from scratch offline.
Swapping in a real dataset later means only changing this file.

Fraud is made to correlate with the same red flags real risk teams watch:
unusual amounts for the user, bursts of activity, big geographic jumps, brand-new
devices, cross-border use, odd hours, and very new cards. Class imbalance
(~1.5% fraud) mirrors reality.
"""
import numpy as np
import pandas as pd

from app.features import FEATURES


def generate(n: int = 60_000, fraud_rate: float = 0.015, seed: int = 42) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    n_fraud = int(n * fraud_rate)
    n_legit = n - n_fraud

    # The distributions deliberately OVERLAP. Some legitimate customers make
    # large, cross-border, high-velocity purchases; some fraud is "stealthy" and
    # looks ordinary. That overlap is what makes the problem realistic — a model
    # that scored AUC 1.0 here would be a red flag, not a success.
    def legit():
        splurge = rng.random() < 0.12          # occasional big legit spender
        travelling = rng.random() < 0.08        # legit customer abroad
        return {
            "amount": rng.gamma(2.0, 90.0 if splurge else 40.0),
            "amount_zscore": rng.normal(1.6 if splurge else 0.0, 1.0),
            "txn_velocity_1h": rng.poisson(3.5 if splurge else 1.3),
            "geo_distance_km": rng.exponential(400.0 if travelling else 12.0),
            "device_new": rng.binomial(1, 0.08),
            "hour_of_day": int(rng.integers(5, 24)) % 24,
            "is_foreign": rng.binomial(1, 0.5 if travelling else 0.05),
            "card_age_days": rng.integers(120, 3000),
        }

    def fraud():
        stealthy = rng.random() < 0.20          # fraud trying to blend in
        return {
            "amount": rng.gamma(2.5, 70.0 if stealthy else 130.0),
            "amount_zscore": rng.normal(1.6 if stealthy else 3.0, 1.1),
            "txn_velocity_1h": rng.poisson(3.0 if stealthy else 6.5),
            "geo_distance_km": rng.exponential(120.0 if stealthy else 650.0),
            "device_new": rng.binomial(1, 0.45 if stealthy else 0.75),
            "hour_of_day": int(rng.choice(list(range(0, 6)) + list(range(22, 24)))),
            "is_foreign": rng.binomial(1, 0.3 if stealthy else 0.5),
            "card_age_days": rng.integers(1, 300),
        }

    rows = []
    for _ in range(n_legit):
        r = legit(); r["is_fraud"] = 0; rows.append(r)
    for _ in range(n_fraud):
        r = fraud(); r["is_fraud"] = 1; rows.append(r)

    df = pd.DataFrame(rows)

    # Label noise: real labels come from chargebacks weeks later, so some fraud is
    # never flagged (missed) and a few disputes are filed on legit charges. We model
    # mostly the "missed fraud" direction, lightly, to keep labels realistic.
    fraud_idx = df.index[df["is_fraud"] == 1]
    legit_idx = df.index[df["is_fraud"] == 0]
    missed = rng.choice(fraud_idx, size=int(0.05 * len(fraud_idx)), replace=False)
    disputed = rng.choice(legit_idx, size=int(0.002 * len(legit_idx)), replace=False)
    df.loc[missed, "is_fraud"] = 0
    df.loc[disputed, "is_fraud"] = 1
    df["hour_of_day"] = df["hour_of_day"].astype(int)
    df["amount"] = df["amount"].clip(lower=1.0).round(2)
    df["geo_distance_km"] = df["geo_distance_km"].round(1)
    # shuffle
    df = df.sample(frac=1.0, random_state=seed).reset_index(drop=True)
    return df[FEATURES + ["is_fraud"]]


if __name__ == "__main__":
    d = generate()
    print(d.head())
    print("fraud rate:", d["is_fraud"].mean().round(4))
