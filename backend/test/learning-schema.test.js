import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const schema = await readFile(new URL('../migrations/003_learning_foundation.sql', import.meta.url), 'utf8');

test('learning migration creates only the approved catalog and append-only event foundation', () => {
  for (const table of ['subjects', 'topics', 'lessons', 'learning_events']) assert.match(schema, new RegExp(`CREATE TABLE IF NOT EXISTS ${table} \\(`, 'u'));
  for (const deferredTable of ['quiz_questions', 'flashcards', 'achievements', 'leaderboard', 'notifications', 'shop', 'review_queue', 'sync']) assert.doesNotMatch(schema, new RegExp(`CREATE TABLE IF NOT EXISTS ${deferredTable} \\(`, 'u'));
});

test('learning events enforce immutable event integrity, ownership, idempotency, and required indexes', () => {
  assert.match(schema, /user_id UUID NOT NULL REFERENCES users\(id\) ON DELETE CASCADE/u);
  assert.match(schema, /lesson_id UUID NOT NULL REFERENCES lessons\(id\) ON DELETE RESTRICT/u);
  assert.match(schema, /xp_earned INTEGER NOT NULL/u);
  assert.match(schema, /CHECK \(xp_earned >= 0\)/u);
  assert.match(schema, /CHECK \(event_type = 'lesson\.completed'\)/u);
  assert.match(schema, /UNIQUE \(user_id, idempotency_key\)/u);
  for (const index of ['subjects_active_display_order_idx', 'topics_subject_active_display_order_idx', 'lessons_topic_active_display_order_idx', 'learning_events_user_occurred_at_idx', 'learning_events_user_lesson_idx']) assert.match(schema, new RegExp(`CREATE INDEX IF NOT EXISTS ${index}`, 'u'));
  assert.doesNotMatch(schema, /UPDATE learning_events|DELETE FROM learning_events/u);
});
