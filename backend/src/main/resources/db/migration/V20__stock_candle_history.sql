-- Separate data identities prevent mixing adjusted/raw prices or different units.
CREATE TABLE stock_feed_cache (
 symbol VARCHAR(10) NOT NULL, timeframe VARCHAR(12) NOT NULL, feed_json TEXT NOT NULL,
 PRIMARY KEY(symbol,timeframe)
);
CREATE TABLE stock_candle_history (
 symbol VARCHAR(10) NOT NULL, timeframe VARCHAR(12) NOT NULL,
 data_identity VARCHAR(64) NOT NULL, candle_time BIGINT NOT NULL,
 open_price DOUBLE PRECISION NOT NULL, high_price DOUBLE PRECISION NOT NULL,
 low_price DOUBLE PRECISION NOT NULL, close_price DOUBLE PRECISION NOT NULL,
 volume DOUBLE PRECISION NOT NULL, closed BOOLEAN NOT NULL,
 source TEXT NOT NULL, unit VARCHAR(30) NOT NULL, price_basis VARCHAR(30) NOT NULL,
 verified BOOLEAN NOT NULL, fetched_at TIMESTAMP WITH TIME ZONE NOT NULL,
 PRIMARY KEY(symbol,timeframe,data_identity,candle_time)
);
