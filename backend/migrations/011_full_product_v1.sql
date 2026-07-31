-- Full Product v1: persistent boss challenges and cosmetic shell economy.

CREATE TABLE IF NOT EXISTS boss_challenges (
  id UUID PRIMARY KEY,
  zone_id UUID NOT NULL REFERENCES subjects(id) ON DELETE RESTRICT,
  title VARCHAR(200) NOT NULL,
  description TEXT,
  passing_percentage NUMERIC(5,2) NOT NULL CHECK (passing_percentage BETWEEN 1 AND 100),
  reward_shells INTEGER NOT NULL CHECK (reward_shells >= 0),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS boss_questions (
  id UUID PRIMARY KEY,
  challenge_id UUID NOT NULL REFERENCES boss_challenges(id) ON DELETE RESTRICT,
  prompt TEXT NOT NULL,
  display_order INTEGER NOT NULL CHECK (display_order >= 0),
  UNIQUE (challenge_id, display_order)
);

CREATE TABLE IF NOT EXISTS boss_answer_options (
  id UUID PRIMARY KEY,
  question_id UUID NOT NULL REFERENCES boss_questions(id) ON DELETE RESTRICT,
  text TEXT NOT NULL,
  display_order INTEGER NOT NULL CHECK (display_order >= 0),
  correct BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE (question_id, display_order)
);

CREATE UNIQUE INDEX IF NOT EXISTS boss_answer_options_one_correct_idx
  ON boss_answer_options (question_id) WHERE correct;

CREATE TABLE IF NOT EXISTS learner_wallets (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  currency VARCHAR(16) NOT NULL DEFAULT 'shell' CHECK (currency = 'shell'),
  balance INTEGER NOT NULL DEFAULT 0 CHECK (balance >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS boss_attempts (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  challenge_id UUID NOT NULL REFERENCES boss_challenges(id) ON DELETE RESTRICT,
  submitted_at TIMESTAMPTZ NOT NULL,
  total_questions INTEGER NOT NULL CHECK (total_questions > 0),
  correct_answers INTEGER NOT NULL CHECK (correct_answers BETWEEN 0 AND total_questions),
  score_percentage NUMERIC(5,2) NOT NULL CHECK (score_percentage BETWEEN 0 AND 100),
  passed BOOLEAN NOT NULL,
  reward_shells INTEGER NOT NULL DEFAULT 0 CHECK (reward_shells >= 0),
  idempotency_key UUID NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  result JSONB NOT NULL,
  accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS boss_attempt_answers (
  attempt_id UUID NOT NULL REFERENCES boss_attempts(id) ON DELETE CASCADE,
  question_id UUID NOT NULL REFERENCES boss_questions(id) ON DELETE RESTRICT,
  selected_option_id UUID NOT NULL REFERENCES boss_answer_options(id) ON DELETE RESTRICT,
  correct BOOLEAN NOT NULL,
  PRIMARY KEY (attempt_id, question_id)
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  amount INTEGER NOT NULL CHECK (amount <> 0),
  transaction_type VARCHAR(32) NOT NULL CHECK (transaction_type IN ('boss_reward', 'shop_purchase')),
  reference_id UUID NOT NULL,
  accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, transaction_type, reference_id)
);

CREATE TABLE IF NOT EXISTS shop_items (
  id UUID PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  description TEXT,
  price_shells INTEGER NOT NULL CHECK (price_shells > 0),
  available BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS learner_inventory (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  item_id UUID NOT NULL REFERENCES shop_items(id) ON DELETE RESTRICT,
  quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
  equipped BOOLEAN NOT NULL DEFAULT FALSE,
  acquired_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, item_id)
);

CREATE TABLE IF NOT EXISTS shop_purchases (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  item_id UUID NOT NULL REFERENCES shop_items(id) ON DELETE RESTRICT,
  price_shells INTEGER NOT NULL CHECK (price_shells > 0),
  idempotency_key UUID NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  result JSONB NOT NULL,
  purchased_at TIMESTAMPTZ NOT NULL,
  UNIQUE (user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS boss_attempts_user_challenge_idx ON boss_attempts (user_id, challenge_id, accepted_at DESC);
CREATE INDEX IF NOT EXISTS boss_attempt_answers_attempt_idx ON boss_attempt_answers (attempt_id);
CREATE INDEX IF NOT EXISTS wallet_transactions_user_idx ON wallet_transactions (user_id, accepted_at DESC);
CREATE INDEX IF NOT EXISTS shop_purchases_user_idx ON shop_purchases (user_id, purchased_at DESC);

INSERT INTO boss_challenges (id, zone_id, title, description, passing_percentage, reward_shells, active) VALUES
  ('019f7e39-0200-7000-8000-000000000001', '019f7e39-0000-7000-8000-000000000001', 'Reef Guardian', 'Show what you learned in the English Foundations zone.', 80, 25, TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO boss_questions (id, challenge_id, prompt, display_order) VALUES
  ('019f7e39-0201-7000-8000-000000000001', '019f7e39-0200-7000-8000-000000000001', 'Which greeting is appropriate in the morning?', 1),
  ('019f7e39-0202-7000-8000-000000000001', '019f7e39-0200-7000-8000-000000000001', 'Which phrase is a farewell?', 2)
ON CONFLICT (id) DO NOTHING;

INSERT INTO boss_answer_options (id, question_id, text, display_order, correct) VALUES
  ('019f7e39-0203-7000-8000-000000000001', '019f7e39-0201-7000-8000-000000000001', 'Good morning', 1, TRUE),
  ('019f7e39-0204-7000-8000-000000000001', '019f7e39-0201-7000-8000-000000000001', 'Good night', 2, FALSE),
  ('019f7e39-0205-7000-8000-000000000001', '019f7e39-0202-7000-8000-000000000001', 'Hello', 1, FALSE),
  ('019f7e39-0206-7000-8000-000000000001', '019f7e39-0202-7000-8000-000000000001', 'Goodbye', 2, TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO shop_items (id, name, description, price_shells, available) VALUES
  ('019f7e39-0210-7000-8000-000000000001', 'Coral profile frame', 'A cosmetic coral frame for your profile.', 20, TRUE),
  ('019f7e39-0211-7000-8000-000000000001', 'Deep sea theme', 'A cosmetic deep sea color theme.', 40, TRUE)
ON CONFLICT (id) DO NOTHING;
