-- Immutable pre-draw tickets; new result revisions append settlement events.
CREATE TABLE forecast_snapshots (
    id VARCHAR(160) PRIMARY KEY,
    snapshot_json TEXT NOT NULL
);
CREATE TABLE forecast_settlements (
    id BIGSERIAL PRIMARY KEY,
    forecast_id VARCHAR(160) NOT NULL REFERENCES forecast_snapshots(id),
    settlement_json TEXT NOT NULL
);
CREATE INDEX idx_forecast_settlements_forecast ON forecast_settlements(forecast_id, id);
