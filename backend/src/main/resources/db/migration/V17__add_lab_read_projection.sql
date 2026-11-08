-- Validate original immutable snapshots in Java once, then reuse their compact read projection.
ALTER TABLE ai_lab_snapshots ADD COLUMN read_projection_json TEXT;
ALTER TABLE ai_lab_snapshots ADD COLUMN read_projection_version VARCHAR(64);
CREATE INDEX idx_lab_read_page ON ai_lab_snapshots
    ((read_projection_json::jsonb #>> '{data,category}'), (read_projection_json::jsonb #>> '{data,targetDate}') DESC, id);
CREATE FUNCTION invalidate_lab_read_projection() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.snapshot_json IS DISTINCT FROM OLD.snapshot_json THEN
        NEW.read_projection_json = NULL;
        NEW.read_projection_version = NULL;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER invalidate_lab_projection BEFORE UPDATE OF snapshot_json ON ai_lab_snapshots
    FOR EACH ROW EXECUTE FUNCTION invalidate_lab_read_projection();
