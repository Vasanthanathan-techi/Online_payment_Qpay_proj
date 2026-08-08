# QPay

QPay is a Java 17/Spring Boot microservices payment platform. This repository contains its design contracts, executable service skeletons, and local infrastructure.

## Modules

| Module | Port | Store |
|---|---:|---|
| `api-gateway` | 8080 | Redis |
| `authentication-service` | 8081 | MySQL, Redis |
| `user-service` | 8082 | MySQL |
| `merchant-service` | 8083 | MySQL |
| `wallet-service` | 8084 | MySQL |
| `payment-service` | 8085 | MySQL, Redis |
| `payout-service` | 8086 | MySQL |
| `notification-service` | 8087 | MongoDB, Redis |
| `reconciliation-service` | 8088 | MySQL |

## Prerequisites

- JDK 17
- Maven 3.6.3 or newer
- Docker with Compose

## Build and run

```powershell
docker compose up -d
mvn clean verify
mvn -pl services/payment-service -am spring-boot:run
```

Run another service by changing the Maven `-pl` value. MySQL-backed services apply their versioned Flyway migrations automatically at startup. The local Compose MySQL instance is exposed on port `13306` to avoid common conflicts with an existing local MySQL installation.

## Acceptance testing

With the infrastructure and all applications running, execute the complete merchant, wallet, payment, webhook, ledger, notification, and reconciliation flow:

```powershell
.\scripts\acceptance\payment-flow.ps1
```

The script expects the local smoke-test principal by default; credentials and URLs can be overridden with parameters. The equivalent chained API requests are available in `postman/QPay-End-to-End.postman_collection.json`.

## EC2 deployment

The production-shaped single-host Compose stack, environment template, deployment script, and security notes are documented in `docs/EC2_DEPLOYMENT.md`. GitHub Actions runs the same full-stack acceptance flow from `.github/workflows/acceptance.yml`.

## Configuration

Production credentials and URLs must be injected through environment variables or a secrets manager. Committed values are local-development defaults only. Important variables include `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `REDIS_HOST`, `MONGODB_URI`, and `JWT_ISSUER_URI`.

## Design sources

- `docs/QPAY_ARCHITECTURE.md`
- `docs/DATABASE_DESIGN.md`
- `api/openapi.yaml`
