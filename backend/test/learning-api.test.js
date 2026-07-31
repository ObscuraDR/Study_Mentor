import assert from 'node:assert/strict';
import test, { afterEach } from 'node:test';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { MemoryIdentityRepository } from './support/memory-identity-repository.js';

const servers = new Set();
const silentLogger = { info() {}, warn() {}, error() {} };
const subjectId = '019f7e39-0000-7000-8000-000000000001';
const topicId = '019f7e39-0001-7000-8000-000000000001';
const lessonId = '019f7e39-0003-7000-8000-000000000001';
const otherLessonId = '019f7e39-0004-7000-8000-000000000001';

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
  const result = await request(baseUrl, '/api/v1/auth/register', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { displayName: 'Learning Student', email: `learning-${Date.now()}@example.com`, password: 'Correct Horse Battery 1' } });
  return result.payload.data.accessToken;
}

function completedEvent(overrides = {}) {
  return { lessonId, occurredAt: new Date().toISOString(), durationSeconds: 120, eventType: 'lesson.completed', ...overrides };
}

test('learning catalog routes are authenticated, ordered, and return canonical envelopes', async () => {
  const { baseUrl } = await api();
  assert.equal((await request(baseUrl, '/api/v1/subjects')).response.status, 401);
  const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}` };
  const subjects = await request(baseUrl, '/api/v1/subjects', { headers });
  assert.equal(subjects.response.status, 200); assert.equal(subjects.payload.data[0].id, subjectId); assert.equal(subjects.payload.meta.requestId, subjects.response.headers.get('x-request-id'));
  const subject = await request(baseUrl, `/api/v1/subjects/${subjectId}`, { headers });
  assert.equal(subject.payload.data.slug, 'english-foundations');
  const topics = await request(baseUrl, `/api/v1/subjects/${subjectId}/topics`, { headers });
  assert.equal(topics.payload.data[0].id, topicId);
  const lessons = await request(baseUrl, `/api/v1/topics/${topicId}/lessons`, { headers });
  assert.equal(lessons.payload.data.length, 2);
  assert.equal((await request(baseUrl, '/api/v1/lessons/019f7e39-ffff-7000-8000-000000000001', { headers })).response.status, 404);
});

test('an immutable learning event creates a server UUIDv7 event and a derived progress projection', async () => {
  const { baseUrl } = await api(); const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}`, 'Idempotency-Key': '019f7e39-0100-7000-8000-000000000001' };
  const result = await request(baseUrl, '/api/v1/learning-events', { method: 'POST', headers, body: completedEvent() });
  assert.equal(result.response.status, 201); assert.match(result.payload.data.event.id, /^[0-9a-f]{8}-[0-9a-f]{4}-7/u);
  assert.match(result.payload.data.event.userId, /^[0-9a-f]{8}-[0-9a-f]{4}-7/u);
  assert.equal(result.payload.data.event.xpEarned, 10);
  assert.deepEqual(result.payload.data.progress, { completedLessons: 1, totalLessons: 3, completedTopics: 0, totalTopics: 2, completedSubjects: 0, totalSubjects: 1, totalXp: 10, learningTimeSeconds: 120, completionPercentage: 33.33 });
  const progress = await request(baseUrl, '/api/v1/me/progress', { headers: { Authorization: headers.Authorization } });
  assert.deepEqual(progress.payload.data, result.payload.data.progress);
});

test('equivalent idempotency replay is safe, retains the original server award, and rejects mismatched reuse', async () => {
  const { baseUrl, repository } = await api(); const authorization = `Bearer ${await accessToken(baseUrl)}`; const key = '019f7e39-0101-7000-8000-000000000001'; const headers = { Authorization: authorization, 'Idempotency-Key': key };
  const body = completedEvent();
  const first = await request(baseUrl, '/api/v1/learning-events', { method: 'POST', headers, body });
  repository.lessons[0].difficulty = 'advanced';
  const replay = await request(baseUrl, '/api/v1/learning-events', { method: 'POST', headers, body });
  assert.equal(first.response.status, 201); assert.equal(replay.response.status, 200); assert.equal(replay.response.headers.get('x-idempotent-replay'), 'true'); assert.equal(replay.payload.data.event.id, first.payload.data.event.id); assert.equal(replay.payload.data.event.xpEarned, 10);
  const conflict = await request(baseUrl, '/api/v1/learning-events', { method: 'POST', headers, body: completedEvent({ lessonId: otherLessonId }) });
  assert.equal(conflict.response.status, 409); assert.equal(conflict.payload.error.code, 'learning.idempotency_key_reused');
  assert.equal((await request(baseUrl, '/api/v1/me/progress', { headers: { Authorization: authorization } })).payload.data.totalXp, 10);
});

test('learning event validation rejects client XP, malformed duration, future, invalid-type, and unknown-lesson submissions', async () => {
  const { baseUrl } = await api(); const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}`, 'Idempotency-Key': '019f7e39-0102-7000-8000-000000000001' };
  for (const body of [
    completedEvent({ xpEarned: 10 }),
    completedEvent({ durationSeconds: -1 }),
    completedEvent({ occurredAt: new Date(Date.now() + 3600_000).toISOString() }),
    completedEvent({ eventType: 'quiz.completed' }),
    { ...completedEvent(), completedLessons: 999 },
  ]) {
    const result = await request(baseUrl, '/api/v1/learning-events', { method: 'POST', headers, body });
    assert.equal(result.response.status, 422); assert.equal(result.payload.error.code, 'validation.invalid_request');
  }
  const unknown = await request(baseUrl, '/api/v1/learning-events', { method: 'POST', headers, body: completedEvent({ lessonId: '019f7e39-ffff-7000-8000-000000000001' }) });
  assert.equal(unknown.response.status, 422); assert.equal(unknown.payload.error.code, 'learning.unknown_lesson');
});

test('the single server-owned award policy derives beginner, intermediate, and advanced XP', async () => {
  const { baseUrl, repository } = await api();
  repository.lessons[1].difficulty = 'intermediate'; repository.lessons[2].difficulty = 'advanced';
  const authorization = `Bearer ${await accessToken(baseUrl)}`;
  const cases = [[lessonId, 10], [otherLessonId, 20], ['019f7e39-0005-7000-8000-000000000001', 30]];
  for (const [lesson, award] of cases) {
    const key = `019f7e39-011${award / 10}-7000-8000-000000000001`;
    const result = await request(baseUrl, '/api/v1/learning-events', { method: 'POST', headers: { Authorization: authorization, 'Idempotency-Key': key }, body: completedEvent({ lessonId: lesson }) });
    assert.equal(result.response.status, 201); assert.equal(result.payload.data.event.xpEarned, award);
  }
});

test('inactive lessons cannot create events', async () => {
  const { baseUrl, repository } = await api(); repository.lessons[0].active = false;
  const result = await request(baseUrl, '/api/v1/learning-events', { method: 'POST', headers: { Authorization: `Bearer ${await accessToken(baseUrl)}`, 'Idempotency-Key': '019f7e39-0119-7000-8000-000000000001' }, body: completedEvent() });
  assert.equal(result.response.status, 422); assert.equal(result.payload.error.code, 'learning.unknown_lesson');
});

test('lesson completions requires authentication', async () => {
  const { baseUrl } = await api();
  assert.equal((await request(baseUrl, '/api/v1/me/lesson-completions')).response.status, 401);
});

test('a user with no completions sees an empty list, not an error', async () => {
  const { baseUrl } = await api(); const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}` };
  const result = await request(baseUrl, '/api/v1/me/lesson-completions', { headers });
  assert.equal(result.response.status, 200); assert.deepEqual(result.payload.data, []);
  assert.equal(result.payload.meta.requestId, result.response.headers.get('x-request-id'));
});

test('one accepted completion is listed with its lessonId and completedAt', async () => {
  const { baseUrl } = await api(); const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}`, 'Idempotency-Key': '019f7e39-0200-7000-8000-000000000001' };
  await request(baseUrl, '/api/v1/learning-events', { method: 'POST', headers, body: completedEvent({ occurredAt: '2026-07-20T08:00:00.000Z' }) });
  const result = await request(baseUrl, '/api/v1/me/lesson-completions', { headers: { Authorization: headers.Authorization } });
  assert.deepEqual(result.payload.data, [{ lessonId, completedAt: '2026-07-20T08:00:00.000Z' }]);
});

test('multiple completed lessons are all listed, each with its own completedAt', async () => {
  const { baseUrl } = await api(); const authorization = `Bearer ${await accessToken(baseUrl)}`;
  await request(baseUrl, '/api/v1/learning-events', { method: 'POST', headers: { Authorization: authorization, 'Idempotency-Key': '019f7e39-0201-7000-8000-000000000001' }, body: completedEvent({ occurredAt: '2026-07-20T08:00:00.000Z' }) });
  await request(baseUrl, '/api/v1/learning-events', { method: 'POST', headers: { Authorization: authorization, 'Idempotency-Key': '019f7e39-0202-7000-8000-000000000001' }, body: completedEvent({ lessonId: otherLessonId, occurredAt: '2026-07-21T08:00:00.000Z' }) });
  const result = await request(baseUrl, '/api/v1/me/lesson-completions', { headers: { Authorization: authorization } });
  assert.deepEqual(result.payload.data, [
    { lessonId, completedAt: '2026-07-20T08:00:00.000Z' },
    { lessonId: otherLessonId, completedAt: '2026-07-21T08:00:00.000Z' },
  ]);
});

test('completing the same lesson twice keeps the earliest completedAt and lists it only once', async () => {
  const { baseUrl } = await api(); const authorization = `Bearer ${await accessToken(baseUrl)}`;
  await request(baseUrl, '/api/v1/learning-events', { method: 'POST', headers: { Authorization: authorization, 'Idempotency-Key': '019f7e39-0203-7000-8000-000000000001' }, body: completedEvent({ occurredAt: '2026-07-20T08:00:00.000Z' }) });
  await request(baseUrl, '/api/v1/learning-events', { method: 'POST', headers: { Authorization: authorization, 'Idempotency-Key': '019f7e39-0204-7000-8000-000000000001' }, body: completedEvent({ occurredAt: '2026-07-22T08:00:00.000Z' }) });
  const result = await request(baseUrl, '/api/v1/me/lesson-completions', { headers: { Authorization: authorization } });
  assert.deepEqual(result.payload.data, [{ lessonId, completedAt: '2026-07-20T08:00:00.000Z' }]);
});

test('one user cannot see another user\'s completions', async () => {
  const { baseUrl } = await api();
  const first = `Bearer ${await accessToken(baseUrl)}`;
  await request(baseUrl, '/api/v1/learning-events', { method: 'POST', headers: { Authorization: first, 'Idempotency-Key': '019f7e39-0205-7000-8000-000000000001' }, body: completedEvent() });
  const second = `Bearer ${await accessToken(baseUrl)}`;
  const result = await request(baseUrl, '/api/v1/me/lesson-completions', { headers: { Authorization: second } });
  assert.deepEqual(result.payload.data, []);
});

test('reading lesson completions never creates a learning event', async () => {
  const { baseUrl, repository } = await api(); const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}` };
  await request(baseUrl, '/api/v1/me/lesson-completions', { headers });
  await request(baseUrl, '/api/v1/me/lesson-completions', { headers });
  assert.equal(repository.events.length, 0);
});
