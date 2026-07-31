import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const contract = JSON.parse(await readFile(new URL('../../contracts/openapi/ai-study-mentor.v1.openapi.json', import.meta.url), 'utf8'));

test('OpenAPI defines the authenticated flashcard surface', () => {
  const surface = [
    ['/flashcard-decks', 'get'],
    ['/flashcard-decks/{deckId}', 'get'],
    ['/me/flashcard-queue', 'get'],
    ['/flashcard-reviews', 'post'],
    ['/me/flashcard-decks/{deckId}/reset', 'post'],
  ];
  for (const [path, method] of surface) {
    assert.ok(contract.paths[path]?.[method], `${method.toUpperCase()} ${path} is missing`);
    assert.deepEqual(contract.paths[path][method].security, [{ bearerAuth: [] }]);
  }
  assert.equal(contract.paths['/flashcard-decks'].get.parameters[0].$ref, '#/components/parameters/LessonIdQuery');
});

test('OpenAPI review request accepts only client-owned facts', () => {
  const request = contract.components.schemas.FlashcardReviewRequest;
  assert.deepEqual(Object.keys(request.properties).sort(), ['cardId', 'outcome', 'reviewedAt']);
  assert.deepEqual(request.required.sort(), ['cardId', 'outcome', 'reviewedAt']);
  assert.equal(request.additionalProperties, false);

  // Scheduling and reward values are server-owned and must be unrepresentable
  // in a request. Checked against property names, not the serialised schema —
  // the description legitimately mentions these words to explain the rule.
  const properties = Object.keys(request.properties);
  for (const forbidden of ['box', 'dueAt', 'xp', 'xpEarned', 'streak', 'mastery', 'coins']) {
    assert.equal(properties.includes(forbidden), false, `request must not accept ${forbidden}`);
  }
});

test('OpenAPI review outcome is an extensible enum, not a boolean', () => {
  const outcome = contract.components.schemas.FlashcardReviewRequest.properties.outcome;
  assert.equal(outcome.type, 'string');
  assert.deepEqual(outcome.enum, ['known', 'forgot']);
});

test('OpenAPI review state is server-derived and carries an algorithm version', () => {
  const state = contract.components.schemas.FlashcardReviewState;
  assert.deepEqual(
    Object.keys(state.properties).sort(),
    ['algorithmVersion', 'box', 'cardId', 'dueAt', 'knownReviews', 'lastReviewedAt', 'totalReviews'],
  );
  assert.equal(state.properties.box.minimum, 1);
  assert.equal(state.properties.box.maximum, 5);
  assert.match(state.description, /must not calculate/i);
});

test('flashcards expose no XP or reward surface anywhere', () => {
  // Reviews earn no XP in v1. This guards the whole flashcard surface, not just
  // the request, so a reward field cannot appear without a policy decision.
  const flashcardSchemas = Object.entries(contract.components.schemas)
    .filter(([name]) => name.startsWith('Flashcard'))
    .map(([, schema]) => JSON.stringify(schema));

  for (const serialised of flashcardSchemas) {
    for (const forbidden of ['xpEarned', 'totalXp', 'streak', 'achievement', 'coins', 'reward']) {
      assert.equal(serialised.includes(forbidden), false, `flashcard schema must not expose ${forbidden}`);
    }
  }
});

test('OpenAPI review submission is idempotent with replay and conflict responses', () => {
  const operation = contract.paths['/flashcard-reviews'].post;
  assert.ok(operation.parameters.some((parameter) => parameter.$ref === '#/components/parameters/IdempotencyKey'));
  assert.ok(operation.responses['201']);
  assert.ok(operation.responses['200']);
  assert.ok(operation.responses['409']);
  assert.ok(operation.responses['422']);
});

test('OpenAPI declares the flashcard error codes', () => {
  const codes = contract.components.schemas.ErrorCode.enum;
  for (const code of [
    'flashcard.deck_not_found',
    'flashcard.card_not_found',
    'flashcard.deck_inactive',
    'flashcard.invalid_review',
    'flashcard.idempotency_key_reused',
  ]) {
    assert.ok(codes.includes(code), `${code} is missing from ErrorCode`);
  }
});
