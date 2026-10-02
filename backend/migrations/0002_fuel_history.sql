-- Fuel price history + market quotes for forecast API (Europe West).

CREATE TABLE IF NOT EXISTS fuel_national_daily (
  country TEXT NOT NULL,
  fuel_id TEXT NOT NULL,
  day TEXT NOT NULL,
  avg_eur REAL NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (country, fuel_id, day)
);

CREATE INDEX IF NOT EXISTS idx_fuel_national_day ON fuel_national_daily(country, day);

CREATE TABLE IF NOT EXISTS fuel_market_daily (
  symbol TEXT NOT NULL,
  day TEXT NOT NULL,
  close REAL NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (symbol, day)
);

CREATE INDEX IF NOT EXISTS idx_fuel_market_day ON fuel_market_daily(symbol, day);
