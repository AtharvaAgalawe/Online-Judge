-- Contests (PRD §7.8): visibility, freeze, and auto-publish marker on the V1 contest
-- tables. Indexes justified by query pattern (PRD §8):
--   idx_contests_times           -> contest listing ordered by time
--   uq_contest_problems_problem  -> one contest per problem AND submission-validation
--                                   lookup by problem_id (unique index covers both)
--   idx_submissions_problem_time -> standings aggregation per problem window
ALTER TABLE contests
  ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN freeze_minutes INT NOT NULL DEFAULT 60,
  ADD COLUMN problems_published_at TIMESTAMPTZ;

ALTER TABLE contests
  ADD CONSTRAINT chk_contests_times CHECK (end_time > start_time);
ALTER TABLE contests
  ADD CONSTRAINT chk_contests_freeze CHECK (freeze_minutes >= 0);

CREATE INDEX idx_contests_times ON contests (start_time, end_time);
CREATE UNIQUE INDEX uq_contest_problems_problem ON contest_problems (problem_id);
CREATE INDEX idx_submissions_problem_time ON submissions (problem_id, submitted_at);
