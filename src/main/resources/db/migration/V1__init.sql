CREATE TABLE tenants (
    id                   UUID PRIMARY KEY,
    name                 TEXT        NOT NULL,
    agency               TEXT        NOT NULL,
    idp_issuer           TEXT        NOT NULL,
    client_id            TEXT        NOT NULL,
    mfa_enforced         BOOLEAN     NOT NULL,
    encryption_at_rest   BOOLEAN     NOT NULL,
    audit_retention_days INTEGER     NOT NULL,
    status               TEXT        NOT NULL,
    idempotency_key      TEXT        NOT NULL UNIQUE,  -- makes retried POSTs safe across replicas
    created_at           TIMESTAMPTZ NOT NULL,
    decided_at           TIMESTAMPTZ
);

CREATE TABLE onboarding_jobs (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID        NOT NULL UNIQUE REFERENCES tenants (id),
    status       TEXT        NOT NULL,
    attempts     INTEGER     NOT NULL DEFAULT 0,
    run_after    TIMESTAMPTZ NOT NULL,
    processed_by TEXT,
    last_error   TEXT,
    created_at   TIMESTAMPTZ NOT NULL
);

-- workers only ever scan queued jobs that are due, so index exactly that
CREATE INDEX idx_jobs_ready ON onboarding_jobs (created_at) WHERE status = 'QUEUED';

CREATE TABLE audit_events (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    actor       TEXT        NOT NULL,
    action      TEXT        NOT NULL,
    detail      TEXT        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_tenant ON audit_events (tenant_id, id);

-- audit trail is append only: block updates and deletes at the database level
CREATE FUNCTION audit_events_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_no_update
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_immutable();
