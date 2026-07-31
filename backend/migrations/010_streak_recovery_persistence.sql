-- GAME-BASED-LEARNING-STREAK-RECOVERY-01A-1: private recovery-claim audit only.
-- No projection, eligibility, reward, or client-facing behavior is introduced here.
CREATE TABLE IF NOT EXISTS streak_recoveries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  policy_version VARCHAR(64) NOT NULL,
  missed_local_date DATE NOT NULL,
  timezone VARCHAR(64) NOT NULL,
  qualifying_action_type VARCHAR(32) NOT NULL,
  qualifying_action_id UUID NOT NULL,
  idempotency_key UUID NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, missed_local_date),
  UNIQUE (user_id, idempotency_key),
  UNIQUE (user_id, qualifying_action_type, qualifying_action_id),
  CHECK (qualifying_action_type IN ('learning_event', 'quiz_attempt', 'flashcard_review'))
);

-- Supports future rolling-window and adjacency eligibility checks without public visibility.
CREATE INDEX IF NOT EXISTS streak_recoveries_user_accepted_at_idx
  ON streak_recoveries (user_id, accepted_at DESC);
CREATE INDEX IF NOT EXISTS streak_recoveries_user_missed_local_date_idx
  ON streak_recoveries (user_id, missed_local_date);
