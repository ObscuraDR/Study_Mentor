import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const contract = JSON.parse(await readFile(new URL('../../contracts/openapi/ai-study-mentor.v1.openapi.json', import.meta.url), 'utf8'));

test('OpenAPI learning-event request leaves XP server-generated and retains idempotency', () => {
  const operation = contract.paths['/learning-events'].post;
  const request = contract.components.schemas.LearningEventRequest;
  const response = contract.components.schemas.LearningEvent;
  assert.deepEqual(request.required, ['lessonId', 'occurredAt', 'durationSeconds', 'eventType']);
  assert.equal(request.properties.xpEarned, undefined);
  assert.equal(response.properties.xpEarned.type, 'integer');
  assert.match(response.properties.xpEarned.description, /Server-derived XP/u);
  assert.equal(operation.parameters[0].$ref, '#/components/parameters/IdempotencyKey');
  assert.match(operation.description, /XP is derived.*server/u);
});

test('OpenAPI lesson-completions is an authenticated read with a minimal, additive shape', () => {
  const operation = contract.paths['/me/lesson-completions'].get;
  assert.deepEqual(operation.security, [{ bearerAuth: [] }]);
  assert.deepEqual(operation.responses[200], { $ref: '#/components/responses/LessonCompletions' });
  assert.deepEqual(operation.responses[401], { $ref: '#/components/responses/SessionExpired' });
  // No new error namespace: the only failure mode is an expired session, same
  // as every other /me/* read.
  assert.deepEqual(Object.keys(operation.responses).sort(), ['200', '401']);

  const item = contract.components.schemas.LessonCompletion;
  assert.deepEqual(item.required, ['lessonId', 'completedAt']);
  assert.equal(item.properties.lessonId.$ref, '#/components/schemas/Uuid');
  assert.equal(item.properties.completedAt.$ref, '#/components/schemas/Timestamp');
  assert.equal(item.additionalProperties, false);

  const envelope = contract.components.schemas.LessonCompletionsEnvelope;
  assert.equal(envelope.properties.data.type, 'array');
  assert.equal(envelope.properties.data.items.$ref, '#/components/schemas/LessonCompletion');
});
