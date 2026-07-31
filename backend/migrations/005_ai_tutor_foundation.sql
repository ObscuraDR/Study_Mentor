-- P5-01B: AI tutor idempotency, admission, and response storage.
-- Each provider call is guarded by a short-lived processing lease.
-- Completed responses are replayable for 24 hours.

CREATE TABLE IF NOT EXISTS ai_tutor_idempotency (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL
    REFERENCES users(id) ON DELETE CASCADE,
  idempotency_key UUID NOT NULL,
  request_fingerprint CHAR(64) NOT NULL,
  lesson_id UUID NOT NULL
    REFERENCES lessons(id) ON DELETE RESTRICT,
  normalized_response JSONB NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'processing',
  processing_started_at TIMESTAMPTZ NOT NULL,
  lease_expires_at TIMESTAMPTZ NOT NULL,
  claim_token UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ NULL,
  expires_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT ai_tutor_idempotency_user_key_unique
    UNIQUE (user_id, idempotency_key),

  CONSTRAINT ai_tutor_idempotency_state_check
    CHECK (state IN ('processing', 'completed')),

  CONSTRAINT ai_tutor_idempotency_processing_response_check
    CHECK (
      state <> 'processing'
      OR normalized_response IS NULL
    ),

  CONSTRAINT ai_tutor_idempotency_completed_response_check
    CHECK (
      state <> 'completed'
      OR normalized_response IS NOT NULL
    ),

  CONSTRAINT ai_tutor_idempotency_completed_at_check
    CHECK (
      state <> 'completed'
      OR completed_at IS NOT NULL
    ),

  CONSTRAINT ai_tutor_idempotency_expires_after_created_check
    CHECK (expires_at > created_at)
);

CREATE INDEX IF NOT EXISTS ai_tutor_idempotency_user_state_idx
  ON ai_tutor_idempotency (user_id, state, lease_expires_at);
CREATE INDEX IF NOT EXISTS ai_tutor_idempotency_user_key_idx
  ON ai_tutor_idempotency (user_id, idempotency_key);
CREATE INDEX IF NOT EXISTS ai_tutor_idempotency_expires_idx
  ON ai_tutor_idempotency (expires_at) WHERE state = 'completed';
CREATE INDEX IF NOT EXISTS ai_tutor_idempotency_abandoned_idx
  ON ai_tutor_idempotency (lease_expires_at) WHERE state = 'processing';
