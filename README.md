# LedgerFlow

A payment orchestration platform built the way real payment systems are built:
correct under retries, exact with money, provably balanced, and consistent when
things go wrong — with an ML fraud model and a natural-language risk analyst
layered on top.

The project itself lives in [`ledgerflow/`](ledgerflow/) — see
**[ledgerflow/README.md](ledgerflow/README.md)** for the full write-up: architecture,
the payment saga, fraud scoring, the risk analyst, quickstart, and the API
reference.

## Repo layout

- [`ledgerflow/`](ledgerflow/) — the Spring Boot payment platform (Java 21)
- [`ledgerflow/fraud-service/`](ledgerflow/fraud-service/) — Python XGBoost + SHAP fraud-scoring service

## Quickstart

```bash
cd ledgerflow
docker compose up --build
```

See [ledgerflow/README.md](ledgerflow/README.md) for the full quickstart, API table,
and how to run tests.
