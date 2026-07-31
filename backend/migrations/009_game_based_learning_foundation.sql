-- GAME-BASED-LEARNING-FOUNDATION-01: additive, backend-authoritative engagement.
ALTER TABLE user_settings ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh';

CREATE TABLE IF NOT EXISTS engagement_awards (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  source_type VARCHAR(32) NOT NULL,
  source_id UUID NOT NULL,
  rule_version VARCHAR(64) NOT NULL,
  award_kind VARCHAR(32) NOT NULL,
  amount INTEGER NOT NULL CHECK (amount > 0),
  accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, source_type, source_id, rule_version),
  CHECK (source_type = 'learning_event'), CHECK (award_kind = 'xp')
);
CREATE TABLE IF NOT EXISTS achievement_entitlements (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  achievement_key VARCHAR(64) NOT NULL,
  rule_version VARCHAR(64) NOT NULL,
  awarded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, achievement_key)
);
CREATE INDEX IF NOT EXISTS engagement_awards_user_idx ON engagement_awards (user_id, accepted_at);
