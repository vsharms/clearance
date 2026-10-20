# Clearance

Clearance is a small service for onboarding new tenants onto a platform that serves government customers. A tenant doesn't go live until a set of security controls has checked out. Every decision lands in an audit trail that can't be edited.

I built it after working on identity and secrets management during my internship at WEX. I wanted to see what onboarding looks like when compliance has to be checked automatically, not just tracked in a spreadsheet.

## What it does

1. An onboarding admin submits a tenant with a few details: its OIDC identity provider, a client secret, and its security settings.
2. The client secret goes straight into HashiCorp Vault. The database only keeps a pointer to it.
3. A background worker picks up the request and runs each control:

| Control | Based on (NIST SP 800-53) | Check |
|---|---|---|
| MFA | IA-2(1) | Tenant enforces multi-factor auth |
| Encryption at rest | SC-28 | Tenant data stores are encrypted |
| Audit retention | AU-11 | Audit logs are kept for at least 90 days |
| Identity provider | IA-8 | OIDC discovery works, the issuer matches, a JWKS endpoint exists, and `alg=none` is not allowed |

4. If every control passes, the tenant becomes `ACTIVE`. If any control fails, it becomes `REJECTED`, and every result is recorded. If the identity provider is unreachable, the worker retries with exponential backoff and marks the tenant `FAILED` after 5 attempts.

These checks are simplified versions of the real controls. This is not a FedRAMP tool.

## How it scales out

```
             ┌────────── nginx :8080 ──────────┐
             ▼                ▼                ▼
          app #1           app #2           app #3      (identical replicas)
             └────────────────┼────────────────┘
                              ▼
                PostgreSQL (tenants, job queue, audit)
```

- **The job queue lives in Postgres.** Each replica claims work with `SELECT ... FOR UPDATE SKIP LOCKED`, so replicas never wait on each other and never process the same tenant twice. There is no separate message broker to run.
- **Requests are idempotent.** `POST /api/tenants` requires an `Idempotency-Key` header, and a unique constraint on that key means a retried request (or two replicas receiving the same request) returns the original tenant instead of creating a duplicate.
- **The audit trail can only be appended to.** A database trigger rejects any `UPDATE` or `DELETE` on `audit_events`.
- **Each replica reports its own state.** Health probes live at `/actuator/health/liveness` and `/readiness`. Metrics (control timings, pass and fail counts, retries, decisions) export over OpenTelemetry when `OTLP_ENABLED=true`.

`OnboardingWorkerIT` runs four workers against the same queue at once and checks that each of 40 tenants is decided exactly once.

## Running it locally

Everything here is free: Docker, Postgres, Vault in dev mode, Keycloak, and nginx.

```bash
docker compose up --build
```

Get a token from Keycloak. It comes with two users: `admin`/`admin` (can submit tenants) and `auditor`/`auditor` (read only).

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/realms/clearance/protocol/openid-connect/token \
  -d grant_type=password -d client_id=clearance-cli -d username=admin -d password=admin \
  | jq -r .access_token)
```

Submit a tenant. Google's public issuer is used here so the identity provider check runs against a real OIDC provider.

```bash
curl -s -X POST http://localhost:8080/api/tenants \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"name":"Records Portal","agency":"Dept. of Example","idpIssuer":"https://accounts.google.com",
       "clientId":"records-portal","clientSecret":"change-me","mfaEnforced":true,
       "encryptionAtRest":true,"auditRetentionDays":365}'
```

Then check the result and read the audit trail. The `actor` field shows which replica made the decision.

```bash
curl -s http://localhost:8080/api/tenants/<id> -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/tenants/<id>/audit -H "Authorization: Bearer $TOKEN"
```

Try `"mfaEnforced": false` or `"auditRetentionDays": 30` to see a rejection.

## Tests

```bash
mvn verify
```

- `ControlsTest` covers each control, including issuer mismatches, `alg=none`, and an unreachable identity provider. These tests run offline.
- `TenantControllerTest` covers auth and roles: no token returns 401, an auditor trying to submit gets 403, retries return 200, and a missing idempotency key returns 400.
- `OnboardingWorkerIT` covers concurrency and idempotency against a real Postgres using Testcontainers. It needs Docker and is skipped without it.

## Stack

Java 21, Spring Boot 3.5, Spring Security (OAuth2 resource server), Spring Data JPA, Flyway, PostgreSQL, Spring Vault, Micrometer with OTLP export, JUnit 5, Testcontainers, Docker Compose, nginx, Keycloak, and GitHub Actions.

## What I'd add next

- Real Vault auth (AppRole) instead of a dev token
- A small React page for reviewers to see pending tenants and why they were rejected
- More controls, for example checking password policy (IA-5) through the identity provider's admin API
