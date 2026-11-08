-- Durable lottery jobs only. Market data and user position inputs never enter this queue.
CREATE TABLE page_job_revision (id INTEGER PRIMARY KEY CHECK (id = 1), revision BIGINT NOT NULL);
INSERT INTO page_job_revision VALUES (1, 0);
CREATE FUNCTION advance_page_job_revision() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW IS NOT DISTINCT FROM OLD THEN RETURN NULL; END IF;
    UPDATE page_job_revision SET revision = revision + 1 WHERE id = 1;
    RETURN NULL;
END;
$$;
CREATE TRIGGER page_revision_draws AFTER INSERT OR UPDATE OR DELETE ON lottery_numbers FOR EACH ROW EXECUTE FUNCTION advance_page_job_revision();
CREATE TRIGGER page_revision_numbers AFTER INSERT OR UPDATE OR DELETE ON lottery_selected_numbers FOR EACH ROW EXECUTE FUNCTION advance_page_job_revision();
CREATE TRIGGER page_revision_parameters AFTER INSERT OR UPDATE OR DELETE ON algorithm_hyperparameters FOR EACH ROW EXECUTE FUNCTION advance_page_job_revision();
CREATE TRIGGER page_revision_lab AFTER INSERT OR DELETE ON ai_lab_snapshots FOR EACH ROW EXECUTE FUNCTION advance_page_job_revision();
CREATE TRIGGER page_revision_lab_update AFTER UPDATE OF snapshot_json ON ai_lab_snapshots FOR EACH ROW WHEN (NEW.snapshot_json IS DISTINCT FROM OLD.snapshot_json) EXECUTE FUNCTION advance_page_job_revision();
CREATE TRIGGER page_revision_settlements AFTER INSERT OR UPDATE OR DELETE ON ai_lab_settlements FOR EACH ROW EXECUTE FUNCTION advance_page_job_revision();

CREATE TABLE page_jobs (
    id VARCHAR(38) PRIMARY KEY,
    job_key TEXT NOT NULL,
    kind VARCHAR(32) NOT NULL,
    input_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
    result_json TEXT,
    cache_seconds INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    lease_until TIMESTAMPTZ,
    claim_token VARCHAR(36),
    attempts INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_page_jobs_key ON page_jobs USING hash(job_key);
CREATE INDEX idx_page_jobs_pending ON page_jobs(status, created_at);
