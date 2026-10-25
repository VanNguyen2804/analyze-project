-- Schema only: historical values are reconciled against validated feeds by the importer.
ALTER TABLE lottery_numbers ADD COLUMN IF NOT EXISTS quality_json TEXT;
