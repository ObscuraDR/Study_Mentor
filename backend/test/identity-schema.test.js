import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const schema = await readFile(new URL('../migrations/001_identity_foundation.sql', import.meta.url), 'utf8');

test('identity migration contains only the P4-02 authoritative tables', () => {
  for (const table of ['users', 'user_profiles', 'refresh_token_families', 'sessions']) assert.match(schema, new RegExp(`CREATE TABLE IF NOT EXISTS ${table} \\(`, 'u'));
  for (const deferredTable of ['subjects', 'quiz', 'progress', 'flashcards', 'achievements', 'notifications', 'sync', 'shop']) assert.doesNotMatch(schema, new RegExp(`CREATE TABLE IF NOT EXISTS ${deferredTable} \\(`, 'u'));
});

test('identity migration hashes refresh tokens and enforces profile/session integrity', () => {
  assert.match(schema, /refresh_token_hash CHAR\(64\) NOT NULL UNIQUE/u);
  assert.doesNotMatch(schema, /refresh_token\s+(?!hash)/u);
  assert.match(schema, /user_id UUID PRIMARY KEY REFERENCES users\(id\) ON DELETE CASCADE/u);
  assert.match(schema, /family_id UUID NOT NULL REFERENCES refresh_token_families\(id\) ON DELETE CASCADE/u);
});
