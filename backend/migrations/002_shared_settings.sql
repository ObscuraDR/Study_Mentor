-- P4-03A: minimal contract-defined shared settings only.
-- Device presentation preferences deliberately remain client-local.

CREATE TABLE IF NOT EXISTS user_settings (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  locale VARCHAR(8) NOT NULL,
  daily_goal_target_xp INTEGER NOT NULL,
  revision VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT user_settings_locale_check CHECK (locale IN ('vi', 'en')),
  CONSTRAINT user_settings_daily_goal_target_xp_check CHECK (daily_goal_target_xp BETWEEN 1 AND 10000)
);

-- Backfill P4-02 identities that predate this ordered migration. Reusing the
-- existing opaque profile revision gives each new settings resource a valid
-- starting ETag; subsequent settings writes receive independent revisions.
INSERT INTO user_settings (user_id, locale, daily_goal_target_xp, revision)
SELECT u.id, 'vi', 300, p.revision
FROM users u
JOIN user_profiles p ON p.user_id = u.id
LEFT JOIN user_settings s ON s.user_id = u.id
WHERE s.user_id IS NULL;
