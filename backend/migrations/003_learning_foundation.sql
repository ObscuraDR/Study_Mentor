-- P4-04: immutable learning events and server-derived progress only.
-- Public identifiers are UUIDv7 values supplied by the application or seed.

CREATE TABLE IF NOT EXISTS subjects (
  id UUID PRIMARY KEY,
  slug VARCHAR(120) NOT NULL UNIQUE,
  name VARCHAR(160) NOT NULL,
  display_order INTEGER NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT subjects_slug_check CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  CONSTRAINT subjects_display_order_check CHECK (display_order >= 0)
);

CREATE TABLE IF NOT EXISTS topics (
  id UUID PRIMARY KEY,
  subject_id UUID NOT NULL REFERENCES subjects(id) ON DELETE RESTRICT,
  slug VARCHAR(120) NOT NULL,
  name VARCHAR(160) NOT NULL,
  display_order INTEGER NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT topics_slug_check CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  CONSTRAINT topics_display_order_check CHECK (display_order >= 0),
  CONSTRAINT topics_subject_slug_unique UNIQUE (subject_id, slug)
);

CREATE TABLE IF NOT EXISTS lessons (
  id UUID PRIMARY KEY,
  topic_id UUID NOT NULL REFERENCES topics(id) ON DELETE RESTRICT,
  slug VARCHAR(120) NOT NULL,
  title VARCHAR(200) NOT NULL,
  description TEXT NOT NULL,
  estimated_minutes INTEGER NOT NULL,
  difficulty VARCHAR(32) NOT NULL,
  display_order INTEGER NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT lessons_slug_check CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  CONSTRAINT lessons_estimated_minutes_check CHECK (estimated_minutes BETWEEN 1 AND 480),
  CONSTRAINT lessons_difficulty_check CHECK (difficulty IN ('beginner', 'intermediate', 'advanced')),
  CONSTRAINT lessons_display_order_check CHECK (display_order >= 0),
  CONSTRAINT lessons_topic_slug_unique UNIQUE (topic_id, slug)
);

CREATE TABLE IF NOT EXISTS learning_events (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE RESTRICT,
  occurred_at TIMESTAMPTZ NOT NULL,
  xp_earned INTEGER NOT NULL,
  duration_seconds INTEGER NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  idempotency_key UUID NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT learning_events_xp_earned_check CHECK (xp_earned >= 0),
  CONSTRAINT learning_events_duration_seconds_check CHECK (duration_seconds >= 0),
  CONSTRAINT learning_events_type_check CHECK (event_type = 'lesson.completed'),
  CONSTRAINT learning_events_user_idempotency_unique UNIQUE (user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS subjects_active_display_order_idx ON subjects (display_order, id) WHERE active;
CREATE INDEX IF NOT EXISTS topics_subject_active_display_order_idx ON topics (subject_id, display_order, id) WHERE active;
CREATE INDEX IF NOT EXISTS lessons_topic_active_display_order_idx ON lessons (topic_id, display_order, id) WHERE active;
CREATE INDEX IF NOT EXISTS learning_events_user_occurred_at_idx ON learning_events (user_id, occurred_at);
CREATE INDEX IF NOT EXISTS learning_events_user_lesson_idx ON learning_events (user_id, lesson_id);

-- Stable UUIDv7 development and test catalog. It is deliberately small and
-- idempotent; production content management is a later product concern.
INSERT INTO subjects (id, slug, name, display_order) VALUES
  ('019f7e39-0000-7000-8000-000000000001', 'english-foundations', 'English Foundations', 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO topics (id, subject_id, slug, name, display_order) VALUES
  ('019f7e39-0001-7000-8000-000000000001', '019f7e39-0000-7000-8000-000000000001', 'greetings', 'Greetings', 1),
  ('019f7e39-0002-7000-8000-000000000001', '019f7e39-0000-7000-8000-000000000001', 'daily-routines', 'Daily routines', 2)
ON CONFLICT (id) DO NOTHING;

INSERT INTO lessons (id, topic_id, slug, title, description, estimated_minutes, difficulty, display_order) VALUES
  ('019f7e39-0003-7000-8000-000000000001', '019f7e39-0001-7000-8000-000000000001', 'hello-and-goodbye', 'Hello and goodbye', 'Use common greetings and farewells in everyday conversations.', 10, 'beginner', 1),
  ('019f7e39-0004-7000-8000-000000000001', '019f7e39-0001-7000-8000-000000000001', 'introducing-yourself', 'Introducing yourself', 'Introduce yourself with a name and a simple personal detail.', 12, 'beginner', 2),
  ('019f7e39-0005-7000-8000-000000000001', '019f7e39-0002-7000-8000-000000000001', 'morning-routine', 'Morning routine', 'Describe a simple morning routine using present-tense verbs.', 15, 'beginner', 1)
ON CONFLICT (id) DO NOTHING;
