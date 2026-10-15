-- 001_init.sql
CREATE TABLE IF NOT EXISTS users (
  phone TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  token TEXT NOT NULL DEFAULT '',
  about TEXT NOT NULL DEFAULT '',
  operator TEXT NOT NULL DEFAULT '',
  push_token TEXT NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS otp_codes (
  phone TEXT PRIMARY KEY,
  code_hash TEXT NOT NULL,
  expires_at BIGINT NOT NULL,
  attempts INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS messages (
  id BIGSERIAL PRIMARY KEY,
  from_phone TEXT NOT NULL,
  to_phone TEXT NOT NULL,
  body TEXT NOT NULL,
  enc BOOLEAN NOT NULL DEFAULT FALSE,
  kind TEXT NOT NULL DEFAULT 'text',
  url TEXT NOT NULL DEFAULT '',
  ts BIGINT NOT NULL,
  delivered BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_messages_inbox ON messages (to_phone, delivered, ts);

CREATE TABLE IF NOT EXISTS blocks (
  owner TEXT NOT NULL,
  address TEXT NOT NULL,
  ts BIGINT NOT NULL,
  PRIMARY KEY (owner, address)
);

CREATE TABLE IF NOT EXISTS reports (
  id BIGSERIAL PRIMARY KEY,
  reporter TEXT NOT NULL,
  address TEXT NOT NULL,
  reason TEXT NOT NULL DEFAULT '',
  ts BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS pub_keys (
  phone TEXT PRIMARY KEY,
  pub TEXT NOT NULL,
  ts BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS media (
  id TEXT PRIMARY KEY,
  owner TEXT NOT NULL,
  name TEXT NOT NULL DEFAULT 'file',
  mime TEXT NOT NULL DEFAULT 'application/octet-stream',
  size INT NOT NULL,
  created_at BIGINT NOT NULL
);

