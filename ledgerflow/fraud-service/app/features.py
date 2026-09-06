"""Single source of truth for the model's feature contract.

Both the training script and the serving API import FEATURES from here, so the
column order the model was trained on can never drift from what the API sends.
"""

FEATURES = [
    "amount",             # transaction amount in major units
    "amount_zscore",      # how unusual this amount is for THIS user
    "txn_velocity_1h",    # number of transactions by this user in the last hour
    "geo_distance_km",    # distance from the user's previous transaction
    "device_new",         # 1 if the device fingerprint is unseen for this user
    "hour_of_day",        # 0-23, captures odd-hour activity
    "is_foreign",         # 1 if the merchant country differs from the card country
    "card_age_days",      # age of the card on file
]

# Human-readable explanations, used to turn a SHAP feature name into a reason
# string a risk analyst (or an API consumer) can actually read.
FEATURE_REASONS = {
    "amount": "transaction amount",
    "amount_zscore": "amount unusual for this user",
    "txn_velocity_1h": "high recent transaction velocity",
    "geo_distance_km": "large distance from last transaction",
    "device_new": "new/unrecognised device",
    "hour_of_day": "unusual time of day",
    "is_foreign": "cross-border transaction",
    "card_age_days": "card age",
}
