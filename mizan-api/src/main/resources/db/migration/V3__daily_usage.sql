-- Story 5.1 (docs/database §3): one row per user and day, plus one row per day with owner_sub '*'
-- for the global cap, so both caps are atomic conditional upserts (no racy SUM). Tokens make the
-- spend budget survive contract deletion and count failed analyses.
-- Reverse: DROP TABLE daily_usage;

CREATE TABLE daily_usage (
  owner_sub     VARCHAR(64) NOT NULL,
  usage_day     DATE        NOT NULL,
  analyses      INT         NOT NULL DEFAULT 0 CHECK (analyses >= 0),
  input_tokens  BIGINT      NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
  output_tokens BIGINT      NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
  PRIMARY KEY (owner_sub, usage_day)
);

-- Admin statistics (Story 5.2): usage per day across users.
CREATE INDEX idx_usage_day ON daily_usage (usage_day);
