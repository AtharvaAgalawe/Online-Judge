-- Seed the role reference data (PRD §16). Idempotent so re-running never breaks.
INSERT INTO roles (name) VALUES ('ROLE_USER'), ('ROLE_ADMIN')
ON CONFLICT (name) DO NOTHING;
