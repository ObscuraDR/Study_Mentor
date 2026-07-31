import assert from 'node:assert/strict';
import test, { afterEach } from 'node:test';
import { v7 as uuidv7 } from 'uuid';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { MemoryIdentityRepository } from './support/memory-identity-repository.js';

const servers = new Set();
const silentLogger = { info() {}, warn() {}, error() {} };
const lessonId = '019f7e39-0003-7000-8000-000000000001';
const quizId = '019f7e39-0006-7000-8000-000000000001';
const inactiveQuizId = '019f7e39-0015-7000-8000-000000000001';
const questionOne = '019f7e39-0007-7000-8000-000000000001';
const questionTwo = '019f7e39-0008-7000-8000-000000000001';
const inactiveQuestion = '019f7e39-0016-7000-8000-000000000001';
const correctOne = '019f7e39-0009-7000-8000-000000000001';
const wrongOne = '019f7e39-0010-7000-8000-000000000001';
const correctTwo = '019f7e39-0013-7000-8000-000000000001';
const wrongTwo = '019f7e39-0012-7000-8000-000000000001';

afterEach(async () => { await Promise.all([...servers].map((server) => new Promise((resolve) => server.close(resolve)))); servers.clear(); });

async function api() {
  const config = loadConfig({ environment: 'test', jwtAccessSecret: 'test-access-secret-that-is-at-least-thirty-two-characters', databaseUrl: 'postgres://test/test' });
  const repository = new MemoryIdentityRepository();
  const app = createApp({ config, repository, logger: silentLogger, enableRateLimit: false });
  const server = await new Promise((resolve) => { const current = app.listen(0, '127.0.0.1', () => resolve(current)); });
  servers.add(server);
  return { baseUrl: `http://127.0.0.1:${server.address().port}`, repository };
}

async function request(baseUrl, path, { method = 'GET', headers = {}, body } = {}) {
  const response = await fetch(`${baseUrl}${path}`, { method, headers: { ...headers, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, body: body === undefined ? undefined : JSON.stringify(body) });
  return { response, payload: response.status === 204 ? undefined : await response.json() };
}

async function accessToken(baseUrl) {
  const result = await request(baseUrl, '/api/v1/auth/register', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { displayName: 'Quiz Student', email: `quiz-${uuidv7()}@example.com`, password: 'Correct Horse Battery 1' } });
  return result.payload.data.accessToken;
}

function completeAttempt(overrides = {}) {
  return {
    quizId,
    answers: [
      { questionId: questionOne, selectedOptionId: correctOne },
      { questionId: questionTwo, selectedOptionId: correctTwo },
    ],
    ...overrides,
  };
}

test('quiz catalog routes are authenticated, ordered, canonical, and do not leak answer keys', async () => {
  const { baseUrl } = await api();
  assert.equal((await request(baseUrl, `/api/v1/quizzes?lessonId=${lessonId}`)).response.status, 401);
  const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}` };
  const list = await request(baseUrl, `/api/v1/quizzes?lessonId=${lessonId}`, { headers });
  assert.equal(list.response.status, 200);
  assert.equal(list.payload.meta.requestId, list.response.headers.get('x-request-id'));
  assert.equal(list.payload.data.length, 1);
  assert.deepEqual(Object.keys(list.payload.data[0]), ['id', 'lessonId', 'title', 'description', 'questionCount', 'displayOrder', 'active']);
  const detail = await request(baseUrl, `/api/v1/quizzes/${quizId}`, { headers });
  assert.equal(detail.response.status, 200);
  assert.equal(detail.payload.data.questions.length, 2);
  assert.equal(detail.payload.data.questions[0].options.length, 3);
  assert.equal(Object.hasOwn(detail.payload.data.questions[0].options[0], 'correct'), false);
  assert.equal(JSON.stringify(detail.payload.data).includes('correctOptionId'), false);
});

test('quiz attempt submission is server-scored, idempotent, and request-id preserving', async () => {
  const { baseUrl, repository } = await api();
  const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}`, 'Idempotency-Key': '019f7e39-0300-7000-8000-000000000001' };
  const first = await request(baseUrl, '/api/v1/quiz-attempts', { method: 'POST', headers, body: completeAttempt() });
  assert.equal(first.response.status, 201);
  assert.equal(first.payload.meta.requestId, first.response.headers.get('x-request-id'));
  assert.equal(first.payload.data.correctAnswers, 2);
  assert.equal(first.payload.data.scorePercentage, 100);
  assert.equal(first.payload.data.questionResults[0].correctOptionId, correctOne);
  assert.equal(Object.hasOwn(first.payload.data, 'xpEarned'), false);
  assert.equal(Object.hasOwn(first.payload.data, 'progress'), false);
  assert.equal(repository.quizAttempts.length, 1);

  const replay = await request(baseUrl, '/api/v1/quiz-attempts', { method: 'POST', headers, body: completeAttempt({ answers: [...completeAttempt().answers].reverse() }) });
  assert.equal(replay.response.status, 200);
  assert.equal(replay.response.headers.get('x-idempotent-replay'), 'true');
  assert.equal(replay.payload.data.attemptId, first.payload.data.attemptId);
  assert.equal(repository.quizAttempts.length, 1);

  const conflict = await request(baseUrl, '/api/v1/quiz-attempts', { method: 'POST', headers, body: completeAttempt({ answers: [{ questionId: questionOne, selectedOptionId: wrongOne }, { questionId: questionTwo, selectedOptionId: correctTwo }] }) });
  assert.equal(conflict.response.status, 409);
  assert.equal(conflict.payload.error.code, 'quiz.idempotency_key_reused');
  assert.equal(conflict.payload.error.requestId, conflict.response.headers.get('x-request-id'));
});

test('quiz attempt validation rejects client-generated score, XP, correctness, completion, timestamps, and missing idempotency', async () => {
  const { baseUrl } = await api();
  const authorization = `Bearer ${await accessToken(baseUrl)}`;
  const missingKey = await request(baseUrl, '/api/v1/quiz-attempts', { method: 'POST', headers: { Authorization: authorization }, body: completeAttempt() });
  assert.equal(missingKey.response.status, 422);
  for (const extra of ['score', 'scorePercentage', 'xpEarned', 'correctAnswers', 'completion', 'achievements', 'submittedAt']) {
    const result = await request(baseUrl, '/api/v1/quiz-attempts', {
      method: 'POST',
      headers: { Authorization: authorization, 'Idempotency-Key': uuidv7() },
      body: { ...completeAttempt(), [extra]: extra === 'submittedAt' ? new Date().toISOString() : 1 },
    });
    assert.equal(result.response.status, 422);
    assert.equal(result.payload.error.code, 'validation.invalid_request');
  }
});

test('quiz attempt semantic errors are canonical and preserve request IDs', async () => {
  const { baseUrl } = await api();
  const authorization = `Bearer ${await accessToken(baseUrl)}`;
  const cases = [
    [completeAttempt({ quizId: '019f7e39-ffff-7000-8000-000000000001' }), 'quiz.not_found'],
    [completeAttempt({ quizId: inactiveQuizId, answers: [{ questionId: inactiveQuestion, selectedOptionId: '019f7e39-0017-7000-8000-000000000001' }] }), 'quiz.inactive'],
    [completeAttempt({ answers: [{ questionId: questionOne, selectedOptionId: correctOne }] }), 'quiz.incomplete_attempt'],
    [completeAttempt({ answers: [{ questionId: questionOne, selectedOptionId: correctOne }, { questionId: questionOne, selectedOptionId: wrongOne }] }), 'quiz.duplicate_answer'],
    [completeAttempt({ answers: [{ questionId: inactiveQuestion, selectedOptionId: correctOne }, { questionId: questionTwo, selectedOptionId: correctTwo }] }), 'quiz.question_not_found'],
    [completeAttempt({ answers: [{ questionId: questionOne, selectedOptionId: correctTwo }, { questionId: questionTwo, selectedOptionId: correctTwo }] }), 'quiz.option_not_found'],
  ];
  for (const [body, code] of cases) {
    const result = await request(baseUrl, '/api/v1/quiz-attempts', { method: 'POST', headers: { Authorization: authorization, 'Idempotency-Key': uuidv7() }, body });
    assert.equal(result.response.status === 404 || result.response.status === 422, true);
    assert.equal(result.payload.error.code, code);
    assert.equal(result.payload.error.requestId, result.response.headers.get('x-request-id'));
  }
});

test('concurrent identical quiz submissions create one attempt and one replay result', async () => {
  const { baseUrl, repository } = await api();
  const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}`, 'Idempotency-Key': '019f7e39-0301-7000-8000-000000000001' };
  const [left, right] = await Promise.all([
    request(baseUrl, '/api/v1/quiz-attempts', { method: 'POST', headers, body: completeAttempt() }),
    request(baseUrl, '/api/v1/quiz-attempts', { method: 'POST', headers, body: completeAttempt() }),
  ]);
  assert.deepEqual([left.response.status, right.response.status].sort(), [200, 201]);
  assert.equal(left.payload.data.attemptId, right.payload.data.attemptId);
  assert.equal(repository.quizAttempts.length, 1);
});
