import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const schema = await readFile(new URL('../migrations/004_quiz_attempt_foundation.sql', import.meta.url), 'utf8');

test('quiz migration creates only the approved catalog and immutable attempt foundation', () => {
  for (const table of ['quizzes', 'quiz_questions', 'quiz_answer_options', 'quiz_attempts', 'quiz_attempt_answers']) {
    assert.match(schema, new RegExp(`CREATE TABLE IF NOT EXISTS ${table} \\(`, 'u'));
  }
  for (const deferredTable of ['flashcards', 'achievements', 'leaderboard', 'notifications', 'shop', 'review_queue', 'sync']) {
    assert.doesNotMatch(schema, new RegExp(`CREATE TABLE IF NOT EXISTS ${deferredTable} \\(`, 'u'));
  }
});

test('quiz persistence enforces lesson ownership, user ownership, scoring integrity, idempotency, and ordering indexes', () => {
  assert.match(schema, /lesson_id UUID NOT NULL REFERENCES lessons\(id\) ON DELETE RESTRICT/u);
  assert.match(schema, /user_id UUID NOT NULL REFERENCES users\(id\) ON DELETE CASCADE/u);
  assert.match(schema, /quiz_id UUID NOT NULL REFERENCES quizzes\(id\) ON DELETE RESTRICT/u);
  assert.match(schema, /question_type VARCHAR\(32\) NOT NULL/u);
  assert.match(schema, /CHECK \(question_type = 'single-choice'\)/u);
  assert.match(schema, /CHECK \(correct_answers >= 0 AND correct_answers <= total_questions\)/u);
  assert.match(schema, /CHECK \(score_percentage >= 0 AND score_percentage <= 100\)/u);
  assert.match(schema, /UNIQUE \(user_id, idempotency_key\)/u);
  assert.match(schema, /CREATE UNIQUE INDEX IF NOT EXISTS quiz_answer_options_one_correct_idx ON quiz_answer_options \(question_id\) WHERE correct/u);
  for (const index of ['quizzes_lesson_active_display_order_idx', 'quiz_questions_quiz_display_order_idx', 'quiz_answer_options_question_display_order_idx', 'quiz_attempts_user_submitted_at_idx', 'quiz_attempts_user_quiz_idx']) {
    assert.match(schema, new RegExp(`CREATE INDEX IF NOT EXISTS ${index}`, 'u'));
  }
  assert.doesNotMatch(schema, /access_token|refresh_token|password_hash/u);
  assert.doesNotMatch(schema, /UPDATE quiz_attempts|DELETE FROM quiz_attempts/u);
});
