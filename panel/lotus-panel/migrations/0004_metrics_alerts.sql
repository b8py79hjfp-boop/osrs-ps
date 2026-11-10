-- Brief §7: scraped every 15 s; after 24 hours averaged into 1-minute rows (`minute`), kept 30 days.
CREATE TABLE panel.metrics (
    ts     timestamptz NOT NULL,
    series text NOT NULL,
    value  double precision NOT NULL,
    minute boolean NOT NULL DEFAULT false
);
CREATE INDEX metrics_series_ts ON panel.metrics (series, ts);
CREATE INDEX metrics_ts ON panel.metrics (ts);

-- Brief §7: every alert, shown on the dashboard; phase 8 also sends them out.
CREATE TABLE panel.alerts (
    id    bigserial PRIMARY KEY,
    at    timestamptz NOT NULL DEFAULT now(),
    title text NOT NULL,
    body  text NOT NULL,
    sent  boolean NOT NULL DEFAULT false
);
CREATE INDEX alerts_at ON panel.alerts (at DESC);
