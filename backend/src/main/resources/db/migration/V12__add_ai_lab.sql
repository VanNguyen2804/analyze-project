-- Separate experimental tickets from production forecasts.
CREATE TABLE ai_lab_snapshots (id VARCHAR(160) PRIMARY KEY, snapshot_json TEXT NOT NULL);
CREATE TABLE ai_lab_settlements (
    id BIGSERIAL PRIMARY KEY,
    forecast_id VARCHAR(160) NOT NULL REFERENCES ai_lab_snapshots(id),
    settlement_json TEXT NOT NULL
);
CREATE INDEX idx_ai_lab_settlements_forecast ON ai_lab_settlements(forecast_id, id);
