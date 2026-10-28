CREATE TABLE prediction_suggestions (
    id VARCHAR(80) PRIMARY KEY,
    category VARCHAR(8) NOT NULL CHECK (category IN ('MEGA','POWER')),
    target_date DATE NOT NULL,
    suggestion_json TEXT NOT NULL
);
CREATE TABLE prediction_user_batches (
    id VARCHAR(80) PRIMARY KEY,
    category VARCHAR(8) NOT NULL CHECK (category IN ('MEGA','POWER')),
    target_date DATE NOT NULL,
    revision INTEGER NOT NULL CHECK (revision > 0),
    request_key VARCHAR(80) NOT NULL UNIQUE,
    payload_hash VARCHAR(64) NOT NULL,
    batch_json TEXT NOT NULL,
    UNIQUE(category, target_date, revision)
);
CREATE TABLE prediction_user_settlements (
    id BIGSERIAL PRIMARY KEY,
    batch_id VARCHAR(80) NOT NULL REFERENCES prediction_user_batches(id),
    settlement_json TEXT NOT NULL
);
CREATE INDEX idx_prediction_suggestions_target ON prediction_suggestions(category, target_date);
CREATE INDEX idx_prediction_user_settlements_batch ON prediction_user_settlements(batch_id,id);
