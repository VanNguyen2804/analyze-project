-- Keep every v1 snapshot intact; a new policy may capture its own batch before the same future draw.
ALTER TABLE mega_portfolio_snapshots
    ADD COLUMN experiment_version VARCHAR(80) NOT NULL DEFAULT 'mega-portfolio-v1';
ALTER TABLE mega_portfolio_snapshots DROP CONSTRAINT mega_portfolio_snapshots_target_date_key;
ALTER TABLE mega_portfolio_snapshots
    ADD CONSTRAINT uq_mega_portfolio_target_version UNIQUE (target_date, experiment_version);
