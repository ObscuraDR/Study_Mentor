-- Phase 7: backend-authoritative flashcard decks, cards and server-scheduled
-- spaced repetition (leitner-5box-v1).
--
-- Review state is derived by the server from immutable review submissions.
-- Clients submit only cardId, outcome and reviewedAt; box, due date and mastery
-- are never client-supplied. Flashcard reviews earn no XP in v1, so this schema
-- deliberately has no XP, streak, coin or achievement column.

CREATE TABLE IF NOT EXISTS flashcard_decks (
  id UUID PRIMARY KEY,
  lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE RESTRICT,
  slug VARCHAR(120) NOT NULL,
  name VARCHAR(160) NOT NULL,
  description TEXT,
  display_order INTEGER NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT flashcard_decks_slug_check CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  CONSTRAINT flashcard_decks_display_order_check CHECK (display_order >= 0),
  CONSTRAINT flashcard_decks_lesson_slug_unique UNIQUE (lesson_id, slug)
);

CREATE TABLE IF NOT EXISTS flashcards (
  id UUID PRIMARY KEY,
  deck_id UUID NOT NULL REFERENCES flashcard_decks(id) ON DELETE RESTRICT,
  front VARCHAR(500) NOT NULL,
  back VARCHAR(2000) NOT NULL,
  hint VARCHAR(500),
  display_order INTEGER NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT flashcards_display_order_check CHECK (display_order >= 0),
  CONSTRAINT flashcards_deck_order_unique UNIQUE (deck_id, display_order)
);

-- Derived per-user review state. One row per (user, card), created on first review.
-- A card with no row is treated as box 1 and due immediately.
CREATE TABLE IF NOT EXISTS flashcard_review_states (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  card_id UUID NOT NULL REFERENCES flashcards(id) ON DELETE CASCADE,
  box INTEGER NOT NULL,
  due_at TIMESTAMPTZ NOT NULL,
  last_reviewed_at TIMESTAMPTZ,
  total_reviews INTEGER NOT NULL DEFAULT 0,
  known_reviews INTEGER NOT NULL DEFAULT 0,
  algorithm_version VARCHAR(64) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, card_id),
  CONSTRAINT flashcard_review_states_box_check CHECK (box >= 1 AND box <= 5),
  CONSTRAINT flashcard_review_states_counts_check
    CHECK (total_reviews >= 0 AND known_reviews >= 0 AND known_reviews <= total_reviews)
);

-- Immutable submissions. The unique (user_id, idempotency_key) constraint plus
-- payload_hash gives the same replay/conflict semantics as learning_events.
CREATE TABLE IF NOT EXISTS flashcard_reviews (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  card_id UUID NOT NULL REFERENCES flashcards(id) ON DELETE RESTRICT,
  outcome VARCHAR(16) NOT NULL,
  reviewed_at TIMESTAMPTZ NOT NULL,
  resulting_box INTEGER NOT NULL,
  resulting_due_at TIMESTAMPTZ NOT NULL,
  algorithm_version VARCHAR(64) NOT NULL,
  idempotency_key UUID NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT flashcard_reviews_outcome_check CHECK (outcome IN ('known', 'forgot')),
  CONSTRAINT flashcard_reviews_box_check CHECK (resulting_box >= 1 AND resulting_box <= 5),
  CONSTRAINT flashcard_reviews_user_idempotency_unique UNIQUE (user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS flashcard_decks_lesson_active_order_idx
  ON flashcard_decks (lesson_id, display_order, id) WHERE active;
CREATE INDEX IF NOT EXISTS flashcards_deck_active_order_idx
  ON flashcards (deck_id, display_order, id) WHERE active;
CREATE INDEX IF NOT EXISTS flashcard_review_states_user_due_idx
  ON flashcard_review_states (user_id, due_at);
CREATE INDEX IF NOT EXISTS flashcard_reviews_user_card_idx
  ON flashcard_reviews (user_id, card_id, reviewed_at DESC);

-- Development fixtures, matching the existing seeded lesson.
INSERT INTO flashcard_decks (id, lesson_id, slug, name, description, display_order, active) VALUES
  ('019f7e39-0020-7000-8000-000000000001', '019f7e39-0003-7000-8000-000000000001', 'greeting-basics', 'Greeting basics', 'Core greetings and farewells.', 1, TRUE),
  ('019f7e39-0021-7000-8000-000000000001', '019f7e39-0004-7000-8000-000000000001', 'inactive-deck', 'Inactive deck', 'Inactive development fixture.', 2, FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO flashcards (id, deck_id, front, back, hint, display_order, active) VALUES
  ('019f7e39-0022-7000-8000-000000000001', '019f7e39-0020-7000-8000-000000000001', 'Good morning', 'A greeting used before midday.', 'Think of sunrise.', 1, TRUE),
  ('019f7e39-0023-7000-8000-000000000001', '019f7e39-0020-7000-8000-000000000001', 'Good evening', 'A greeting used after about 6pm.', NULL, 2, TRUE),
  ('019f7e39-0024-7000-8000-000000000001', '019f7e39-0020-7000-8000-000000000001', 'Goodbye', 'A farewell used when leaving.', NULL, 3, TRUE),
  ('019f7e39-0025-7000-8000-000000000001', '019f7e39-0021-7000-8000-000000000001', 'Inactive front', 'Inactive back.', NULL, 1, TRUE)
ON CONFLICT (id) DO NOTHING;
