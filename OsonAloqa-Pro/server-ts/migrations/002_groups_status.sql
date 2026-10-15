-- 002_groups_status.sql
CREATE TABLE IF NOT EXISTS groups (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  type TEXT NOT NULL DEFAULT 'group',
  owner TEXT NOT NULL,
  created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS group_members (
  group_id TEXT NOT NULL,
  phone TEXT NOT NULL,
  joined_at BIGINT NOT NULL,
  PRIMARY KEY (group_id, phone)
);

CREATE TABLE IF NOT EXISTS statuses (
  id BIGSERIAL PRIMARY KEY,
  phone TEXT NOT NULL,
  text TEXT NOT NULL DEFAULT '',
  media TEXT NOT NULL DEFAULT '',
  ts BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_statuses_ts ON statuses (ts DESC);
