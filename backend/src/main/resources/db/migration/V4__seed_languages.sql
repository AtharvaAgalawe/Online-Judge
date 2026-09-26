-- Reference data for the two MVP languages (PRD §35). Image names match the runtime
-- images built under infrastructure/docker/images (PRD §30). Idempotent.
INSERT INTO languages (name, source_filename, compile_cmd, run_cmd, docker_image, time_limit_multiplier)
VALUES
  ('Java 21',   'Main.java', 'javac Main.java', 'java Main',      'oj-java21',    1.00),
  ('Python 3.12', 'main.py', NULL,              'python3 main.py', 'oj-python312', 1.00)
ON CONFLICT (name) DO NOTHING;
