import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const migration = await readFile(new URL('../migrations/010_streak_recovery_persistence.sql', import.meta.url), 'utf8');

test('streak recovery migration is additive, private, and audit-only', () => {
  assert.match(migration, /CREATE TABLE IF NOT EXISTS streak_recoveries/u);
  assert.match(migration, /user_id UUID NOT NULL REFERENCES users\(id\) ON DELETE CASCADE/u);
  assert.match(migration, /missed_local_date DATE NOT NULL/u);
  assert.match(migration, /idempotency_key UUID NOT NULL/u);
  assert.match(migration, /payload_hash CHAR\(64\) NOT NULL/u);
  assert.match(migration, /UNIQUE \(user_id, missed_local_date\)/u);
  assert.match(migration, /UNIQUE \(user_id, idempotency_key\)/u);
  assert.match(migration, /streak_recoveries_user_accepted_at_idx/u);
  assert.doesNotMatch(migration, /CREATE TABLE IF NOT EXISTS (?:xp_awards|currencies|missions|leaderboards)/iu);
});
