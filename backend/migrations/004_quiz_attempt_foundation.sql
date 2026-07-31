-- P4-08A: backend-authoritative quiz catalog and immutable scored attempts.
-- Attempts are separate source records; progress integration is intentionally
-- deferred until quiz XP/completion policy is approved.

CREATE TABLE IF NOT EXISTS quizzes (
  id UUID PRIMARY KEY,
  lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE RESTRICT,
  slug VARCHAR(120) NOT NULL,
  title VARCHAR(200) NOT NULL,
  description TEXT,
  display_order INTEGER NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT quizzes_slug_check CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  CONSTRAINT quizzes_display_order_check CHECK (display_order >= 0),
  CONSTRAINT quizzes_lesson_slug_unique UNIQUE (lesson_id, slug)
);

CREATE TABLE IF NOT EXISTS quiz_questions (
  id UUID PRIMARY KEY,
  quiz_id UUID NOT NULL REFERENCES quizzes(id) ON DELETE RESTRICT,
  prompt TEXT NOT NULL,
  question_type VARCHAR(32) NOT NULL,
  display_order INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT quiz_questions_type_check CHECK (question_type = 'single-choice'),
  CONSTRAINT quiz_questions_display_order_check CHECK (display_order >= 0),
  CONSTRAINT quiz_questions_quiz_order_unique UNIQUE (quiz_id, display_order)
);

CREATE TABLE IF NOT EXISTS quiz_answer_options (
  id UUID PRIMARY KEY,
  question_id UUID NOT NULL REFERENCES quiz_questions(id) ON DELETE RESTRICT,
  text TEXT NOT NULL,
  display_order INTEGER NOT NULL,
  correct BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT quiz_answer_options_display_order_check CHECK (display_order >= 0),
  CONSTRAINT quiz_answer_options_question_order_unique UNIQUE (question_id, display_order)
);

CREATE TABLE IF NOT EXISTS quiz_attempts (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  quiz_id UUID NOT NULL REFERENCES quizzes(id) ON DELETE RESTRICT,
  submitted_at TIMESTAMPTZ NOT NULL,
  total_questions INTEGER NOT NULL,
  correct_answers INTEGER NOT NULL,
  score_percentage NUMERIC(5,2) NOT NULL,
  idempotency_key UUID NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  result JSONB NOT NULL,
  accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT quiz_attempts_total_questions_check CHECK (total_questions > 0),
  CONSTRAINT quiz_attempts_correct_answers_check CHECK (correct_answers >= 0 AND correct_answers <= total_questions),
  CONSTRAINT quiz_attempts_score_percentage_check CHECK (score_percentage >= 0 AND score_percentage <= 100),
  CONSTRAINT quiz_attempts_user_idempotency_unique UNIQUE (user_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS quiz_attempt_answers (
  attempt_id UUID NOT NULL REFERENCES quiz_attempts(id) ON DELETE CASCADE,
  question_id UUID NOT NULL REFERENCES quiz_questions(id) ON DELETE RESTRICT,
  selected_option_id UUID NOT NULL REFERENCES quiz_answer_options(id) ON DELETE RESTRICT,
  correct BOOLEAN NOT NULL,
  PRIMARY KEY (attempt_id, question_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS quiz_answer_options_one_correct_idx ON quiz_answer_options (question_id) WHERE correct;
CREATE INDEX IF NOT EXISTS quizzes_lesson_active_display_order_idx ON quizzes (lesson_id, display_order, id) WHERE active;
CREATE INDEX IF NOT EXISTS quiz_questions_quiz_display_order_idx ON quiz_questions (quiz_id, display_order, id);
CREATE INDEX IF NOT EXISTS quiz_answer_options_question_display_order_idx ON quiz_answer_options (question_id, display_order, id);
CREATE INDEX IF NOT EXISTS quiz_attempts_user_submitted_at_idx ON quiz_attempts (user_id, submitted_at DESC);
CREATE INDEX IF NOT EXISTS quiz_attempts_user_quiz_idx ON quiz_attempts (user_id, quiz_id);
CREATE INDEX IF NOT EXISTS quiz_attempt_answers_attempt_idx ON quiz_attempt_answers (attempt_id);

INSERT INTO quizzes (id, lesson_id, slug, title, description, display_order, active) VALUES
  ('019f7e39-0006-7000-8000-000000000001', '019f7e39-0003-7000-8000-000000000001', 'hello-and-goodbye-check', 'Hello and goodbye check', 'Check common greetings and farewells.', 1, TRUE),
  ('019f7e39-0015-7000-8000-000000000001', '019f7e39-0004-7000-8000-000000000001', 'inactive-introduction-check', 'Inactive introduction check', 'Inactive development fixture.', 2, FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO quiz_questions (id, quiz_id, prompt, question_type, display_order) VALUES
  ('019f7e39-0007-7000-8000-000000000001', '019f7e39-0006-7000-8000-000000000001', 'Which greeting is appropriate in the morning?', 'single-choice', 1),
  ('019f7e39-0008-7000-8000-000000000001', '019f7e39-0006-7000-8000-000000000001', 'Which phrase is a farewell?', 'single-choice', 2),
  ('019f7e39-0016-7000-8000-000000000001', '019f7e39-0015-7000-8000-000000000001', 'Inactive question?', 'single-choice', 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO quiz_answer_options (id, question_id, text, display_order, correct) VALUES
  ('019f7e39-0009-7000-8000-000000000001', '019f7e39-0007-7000-8000-000000000001', 'Good morning', 1, TRUE),
  ('019f7e39-0010-7000-8000-000000000001', '019f7e39-0007-7000-8000-000000000001', 'Good night', 2, FALSE),
  ('019f7e39-0011-7000-8000-000000000001', '019f7e39-0007-7000-8000-000000000001', 'See you later', 3, FALSE),
  ('019f7e39-0012-7000-8000-000000000001', '019f7e39-0008-7000-8000-000000000001', 'Hello', 1, FALSE),
  ('019f7e39-0013-7000-8000-000000000001', '019f7e39-0008-7000-8000-000000000001', 'Goodbye', 2, TRUE),
  ('019f7e39-0014-7000-8000-000000000001', '019f7e39-0008-7000-8000-000000000001', 'Good afternoon', 3, FALSE),
  ('019f7e39-0017-7000-8000-000000000001', '019f7e39-0016-7000-8000-000000000001', 'Inactive answer', 1, TRUE)
ON CONFLICT (id) DO NOTHING;
