-- One immutable, complete batch per MEGA draw, separate from AI Lab and user tickets.
CREATE TABLE mega_portfolio_snapshots (
    id VARCHAR(160) PRIMARY KEY,
    target_date DATE NOT NULL UNIQUE,
    snapshot_json TEXT NOT NULL
);
CREATE TABLE mega_portfolio_settlements (
    id BIGSERIAL PRIMARY KEY,
    forecast_id VARCHAR(160) NOT NULL REFERENCES mega_portfolio_snapshots(id),
    settlement_json TEXT NOT NULL
);
CREATE INDEX idx_mega_portfolio_settlements_forecast ON mega_portfolio_settlements(forecast_id, id);
