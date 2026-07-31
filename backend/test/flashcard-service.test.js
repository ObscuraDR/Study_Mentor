import assert from 'node:assert/strict';
import test from 'node:test';
import { ALGORITHM_VERSION, applyLeitnerReview, initialReviewState } from '../src/services/flashcard-service.js';

const DAY = 24 * 60 * 60 * 1000;
const base = '2026-07-23T08:00:00.000Z';
const baseMs = Date.parse(base);

function daysAfterBase(iso) {
  return (Date.parse(iso) - baseMs) / DAY;
}

test('leitner-5box-v1 promotes on known and schedules using the NEW box', () => {
  // The exponent uses the promoted box, so a first success is two days out, not one.
  assert.deepEqual(applyLeitnerReview({ box: 1, outcome: 'known', reviewedAt: base }), {
    box: 2,
    dueAt: new Date(baseMs + 2 * DAY).toISOString(),
  });

  const fromBox2 = applyLeitnerReview({ box: 2, outcome: 'known', reviewedAt: base });
  assert.equal(fromBox2.box, 3);
  assert.equal(daysAfterBase(fromBox2.dueAt), 4);

  const fromBox3 = applyLeitnerReview({ box: 3, outcome: 'known', reviewedAt: base });
  assert.equal(fromBox3.box, 4);
  assert.equal(daysAfterBase(fromBox3.dueAt), 8);

  const fromBox4 = applyLeitnerReview({ box: 4, outcome: 'known', reviewedAt: base });
  assert.equal(fromBox4.box, 5);
  assert.equal(daysAfterBase(fromBox4.dueAt), 16);
});

test('box 5 is the ceiling and keeps its 16 day interval', () => {
  const result = applyLeitnerReview({ box: 5, outcome: 'known', reviewedAt: base });
  assert.equal(result.box, 5);
  assert.equal(daysAfterBase(result.dueAt), 16);
});

test('forgot resets to box 1 and makes the card due immediately', () => {
  for (const box of [1, 2, 3, 4, 5]) {
    const result = applyLeitnerReview({ box, outcome: 'forgot', reviewedAt: base });
    assert.equal(result.box, 1, `box ${box} should reset to 1`);
    assert.equal(result.dueAt, new Date(baseMs).toISOString());
  }
});

test('scheduling is relative to when the review happened, not to now', () => {
  const yesterday = new Date(baseMs - DAY).toISOString();
  const result = applyLeitnerReview({ box: 1, outcome: 'known', reviewedAt: yesterday });
  assert.equal(Date.parse(result.dueAt), Date.parse(yesterday) + 2 * DAY);
});

test('an out-of-range stored box is clamped rather than trusted', () => {
  assert.equal(applyLeitnerReview({ box: 99, outcome: 'known', reviewedAt: base }).box, 5);
  assert.equal(applyLeitnerReview({ box: 0, outcome: 'known', reviewedAt: base }).box, 2);
  assert.equal(applyLeitnerReview({ box: undefined, outcome: 'known', reviewedAt: base }).box, 2);
});

test('a never-reviewed card starts at box 1, due immediately, with the algorithm version', () => {
  const state = initialReviewState('card-1', base);
  assert.deepEqual(state, {
    cardId: 'card-1',
    box: 1,
    dueAt: base,
    lastReviewedAt: null,
    totalReviews: 0,
    knownReviews: 0,
    algorithmVersion: ALGORITHM_VERSION,
  });
});

test('the algorithm version is the approved one', () => {
  assert.equal(ALGORITHM_VERSION, 'leitner-5box-v1');
});

test('scheduling never returns an XP, streak or reward field', () => {
  // Flashcard reviews earn no XP in v1. Guards against a later addition made
  // without a reward-policy decision.
  const result = applyLeitnerReview({ box: 1, outcome: 'known', reviewedAt: base });
  assert.deepEqual(Object.keys(result).sort(), ['box', 'dueAt']);

  const state = initialReviewState('card-1', base);
  for (const forbidden of ['xp', 'xpEarned', 'streak', 'coins', 'achievements', 'reward']) {
    assert.ok(!(forbidden in state), `review state must not expose ${forbidden}`);
  }
});
