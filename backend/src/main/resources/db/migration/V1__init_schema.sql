-- V1__init_schema.sql
-- Initial schema, transcribed from PRD.md §13. This migration is immutable once merged
-- (AGENTS.md §23) — fix forward with a new migration.
--
-- FK ON DELETE behavior (AGENTS.md §8): choices not documented in PRD §13 are noted
-- inline. Omitted "ON DELETE" means the PostgreSQL default (NO ACTION = restrict).

-- Identity -------------------------------------------------------
CREATE TABLE roles (
  id            SERIAL PRIMARY KEY,
  name          VARCHAR(30) NOT NULL UNIQUE          -- ROLE_USER, ROLE_ADMIN
);

CREATE TABLE users (
  id             BIGSERIAL PRIMARY KEY,
  username       VARCHAR(50)  NOT NULL UNIQUE,
  email          VARCHAR(255) NOT NULL UNIQUE,
  password_hash  VARCHAR(255) NOT NULL,
  is_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_roles (
  -- CASCADE on user deletion: roles die with the user; role rows are shared
  -- reference data and are never deleted independently.
  user_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role_id  INT    NOT NULL REFERENCES roles(id),
  PRIMARY KEY (user_id, role_id)
);

-- Catalog ----------------------------------------------------------
CREATE TABLE languages (
  id                     SERIAL PRIMARY KEY,
  name                   VARCHAR(50)  NOT NULL UNIQUE,   -- "Java 21", "Python 3.12", "C++17"
  source_filename        VARCHAR(100) NOT NULL,          -- "Main.java"
  compile_cmd            TEXT,                           -- NULL for interpreted languages
  run_cmd                TEXT NOT NULL,
  docker_image           VARCHAR(200) NOT NULL,
  time_limit_multiplier  NUMERIC(3,2) NOT NULL DEFAULT 1.0,
  is_enabled             BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE tags (
  id    SERIAL PRIMARY KEY,
  name  VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE problems (
  id              BIGSERIAL PRIMARY KEY,
  slug            VARCHAR(120) NOT NULL UNIQUE,
  title           VARCHAR(200) NOT NULL,
  statement       TEXT NOT NULL,
  difficulty      VARCHAR(20) NOT NULL CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
  time_limit_ms   INT NOT NULL DEFAULT 2000,
  memory_limit_kb INT NOT NULL DEFAULT 262144,
  is_published    BOOLEAN NOT NULL DEFAULT FALSE,
  created_by      BIGINT REFERENCES users(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_problems_published ON problems (is_published);

CREATE TABLE problem_tags (
  problem_id  BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
  tag_id      INT    NOT NULL REFERENCES tags(id),
  PRIMARY KEY (problem_id, tag_id)
);

CREATE TABLE test_cases (
  id               BIGSERIAL PRIMARY KEY,
  problem_id       BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
  input            TEXT NOT NULL,
  expected_output  TEXT NOT NULL,
  is_sample        BOOLEAN NOT NULL DEFAULT FALSE,
  display_order    INT NOT NULL DEFAULT 0,
  points           INT NOT NULL DEFAULT 1,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_test_cases_problem ON test_cases (problem_id, display_order);

-- Judging -----------------------------------------------------------
CREATE TABLE submissions (
  -- No ON DELETE: a user's submission history must never be dropped by deleting
  -- the user (audit integrity); deleting a problem with submissions is blocked.
  id                   BIGSERIAL PRIMARY KEY,
  user_id              BIGINT NOT NULL REFERENCES users(id),
  problem_id           BIGINT NOT NULL REFERENCES problems(id),
  language_id          INT NOT NULL REFERENCES languages(id),
  source_code          TEXT NOT NULL,
  status               VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
  verdict              VARCHAR(30),
  time_used_ms         INT,
  memory_used_kb       INT,
  failed_test_case_id  BIGINT REFERENCES test_cases(id),
  error_message        TEXT,
  idempotency_key      VARCHAR(100) UNIQUE,
  version              INT NOT NULL DEFAULT 0,
  submitted_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  judged_at            TIMESTAMPTZ
);
CREATE INDEX idx_submissions_user_time ON submissions (user_id, submitted_at DESC);
CREATE INDEX idx_submissions_problem_user ON submissions (problem_id, user_id);
CREATE INDEX idx_submissions_status ON submissions (status);

CREATE TABLE submission_results (
  id              BIGSERIAL PRIMARY KEY,
  -- CASCADE: results are owned by their submission; deleting a submission removes
  -- its per-test-case rows. Deleting a referenced test_case is blocked (no ON DELETE)
  -- so judged history is never silently rewritten (PRD §7.7 edge cases).
  submission_id   BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  test_case_id    BIGINT NOT NULL REFERENCES test_cases(id),
  verdict         VARCHAR(30) NOT NULL,
  time_used_ms    INT,
  memory_used_kb  INT,
  stdout_snippet  VARCHAR(2000),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (submission_id, test_case_id)
);

CREATE TABLE execution_jobs (
  id                BIGSERIAL PRIMARY KEY,
  submission_id     BIGINT NOT NULL UNIQUE REFERENCES submissions(id) ON DELETE CASCADE,
  locked_by         VARCHAR(100),
  locked_at         TIMESTAMPTZ,
  lease_expires_at  TIMESTAMPTZ,
  retry_count       INT NOT NULL DEFAULT 0,
  max_retries       INT NOT NULL DEFAULT 3,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Contests (P2) -------------------------------------------------------
CREATE TABLE contests (
  id           BIGSERIAL PRIMARY KEY,
  name         VARCHAR(200) NOT NULL,
  description  TEXT,
  start_time   TIMESTAMPTZ NOT NULL,
  end_time     TIMESTAMPTZ NOT NULL,
  created_by   BIGINT REFERENCES users(id),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE contest_problems (
  contest_id     BIGINT NOT NULL REFERENCES contests(id) ON DELETE CASCADE,
  problem_id     BIGINT NOT NULL REFERENCES problems(id),
  display_order  INT NOT NULL DEFAULT 0,
  points         INT NOT NULL DEFAULT 100,
  PRIMARY KEY (contest_id, problem_id)
);

CREATE TABLE contest_participants (
  contest_id     BIGINT NOT NULL REFERENCES contests(id) ON DELETE CASCADE,
  user_id        BIGINT NOT NULL REFERENCES users(id),
  registered_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (contest_id, user_id)
);
