-- Refresh tokens are opaque random strings, stored only as SHA-256 hashes. Keeping them
-- in Postgres (rather than Redis) keeps authentication functional without the cache tier;
-- the worker never touches this table (backend-only concern).
CREATE TABLE refresh_tokens (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256 hex of the raw token
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Logout revokes all of a user's active refresh tokens → this lookup needs the index.
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
