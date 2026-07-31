import assert from 'node:assert/strict';
import test, { afterEach } from 'node:test';
import { v7 as uuidv7 } from 'uuid';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { MemoryIdentityRepository } from './support/memory-identity-repository.js';
import { createRateAdmission } from '../src/rate-admission.js';
import { createFakeTutorProvider } from '../src/fake-tutor-provider.js';
import { AiTutorService } from '../src/services/ai-tutor-service.js';
import { withTutorAdmissionLock } from '../src/admission-mutex.js';

const servers = new Set();
const silentLogger = { info() {}, warn() {}, error() {} };
const lessonId = '019f7e39-0003-7000-8000-000000000001';

afterEach(async () => {
  await Promise.all([...servers].map((server) => new Promise((resolve) => server.close(resolve))));
  servers.clear();
});

async function api(opts = {}) {
  const {
    burstLimit = 5, burstWindowMs = 60000, rollingLimit = 20, rollingWindowMs = 60000,
    concurrencyLimit = 1, leaseDurationSeconds = 30, providerTimeoutMs = 15000,
  } = opts;
  const config = loadConfig({ environment: 'test', jwtAccessSecret: 'test-access-secret-that-is-at-least-thirty-two-characters', databaseUrl: 'postgres://test/test' });
  const repository = new MemoryIdentityRepository();
  const rateAdmission = createRateAdmission({ burstLimit, burstWindowMs, rollingLimit, rollingWindowMs });
  const fakeProvider = createFakeTutorProvider({ defaultTimeoutMs: providerTimeoutMs });
  const app = createApp({
    config: { ...config, aiLeaseDurationSeconds: leaseDurationSeconds, aiConcurrencyLimit: concurrencyLimit, aiProviderTimeoutMs: providerTimeoutMs },
    repository, logger: silentLogger, enableRateLimit: false,
    rateAdmission, fakeProvider,
  });
  const server = await new Promise((resolve) => { const current = app.listen(0, '127.0.0.1', () => resolve(current)); });
  servers.add(server);
  return { baseUrl: `http://127.0.0.1:${server.address().port}`, repository, rateAdmission, fakeProvider };
}

async function request(baseUrl, path, { method = 'GET', headers = {}, body } = {}) {
  const response = await fetch(`${baseUrl}${path}`, { method, headers: { ...headers, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, body: body === undefined ? undefined : JSON.stringify(body) });
  return { response, payload: response.status === 204 ? undefined : await response.json() };
}

async function accessToken(baseUrl) {
  const result = await request(baseUrl, '/api/v1/auth/register', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { displayName: 'Tutor Student', email: `tutor-${Date.now()}@example.com`, password: 'Correct Horse Battery 1' } });
  return result.payload.data.accessToken;
}

// -- Route tests --

test('ai tutor endpoint rejects unauthenticated requests', async () => {
  const { baseUrl } = await api();
  const result = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', body: { lessonId, message: 'How do I say hello?' } });
  assert.equal(result.response.status, 401);
});

test('ai tutor endpoint requires valid Idempotency-Key', async () => {
  const { baseUrl } = await api();
  const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}` };
  const result = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers, body: { lessonId, message: 'Test' } });
  assert.equal(result.response.status, 422);
  assert.equal(result.payload.error.code, 'validation.invalid_request');
});

test('ai tutor endpoint validates TutorResponseRequest fields', async () => {
  const { baseUrl } = await api();
  const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}`, 'Idempotency-Key': uuidv7() };

  const missing = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers, body: { lessonId } });
  assert.equal(missing.response.status, 422);

  const empty = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers, body: { lessonId, message: ' ' }, headers });
  assert.equal(empty.response.status, 422);

  const extra = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers, body: { lessonId, message: 'Test', xpClaimed: 100 } });
  assert.equal(extra.response.status, 422);
});

test('ai tutor endpoint rejects unknown lesson', async () => {
  const { baseUrl } = await api();
  const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}`, 'Idempotency-Key': uuidv7() };
  const result = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers, body: { lessonId: '019f7e39-ffff-7000-8000-000000000001', message: 'Test' } });
  assert.equal(result.response.status, 404);
  assert.equal(result.payload.error.code, 'learning.resource_not_found');
});

// -- Happy path and idempotency tests --

test('first tutor request creates a new response', async () => {
  const { baseUrl } = await api();
  const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}`, 'Idempotency-Key': uuidv7() };
  const result = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers, body: { lessonId, message: 'How do I say hello?' } });
  assert.equal(result.response.status, 201);
  assert.match(result.payload.data.responseId, /^[0-9a-f]{8}-[0-9a-f]{4}-7/u);
  assert.equal(result.payload.data.lessonId, lessonId);
  assert.ok(result.payload.data.answer.length > 0);
  assert.match(result.payload.data.status, /^(completed|truncated|refused)$/);
});

test('identical idempotency replay returns 200 with X-Idempotent-Replay', async () => {
  const { baseUrl } = await api();
  const key = uuidv7();
  const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}`, 'Idempotency-Key': key };
  const body = { lessonId, message: 'Replay test message' };
  const first = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers, body });
  assert.equal(first.response.status, 201);
  const replay = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers, body });
  assert.equal(replay.response.status, 200);
  assert.equal(replay.response.headers.get('x-idempotent-replay'), 'true');
  assert.equal(replay.payload.data.responseId, first.payload.data.responseId);
  assert.equal(replay.payload.data.answer, first.payload.data.answer);
});

test('fingerprint mismatch returns 409', async () => {
  const { baseUrl } = await api();
  const key = uuidv7();
  const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}`, 'Idempotency-Key': key };
  const first = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers, body: { lessonId, message: 'First message' } });
  assert.equal(first.response.status, 201);
  const conflict = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers, body: { lessonId, message: 'Different message' } });
  assert.equal(conflict.response.status, 409);
  assert.equal(conflict.payload.error.code, 'ai.idempotency_key_reused');
});

test('refusal creates a completed response with status=refused', async () => {
  const { baseUrl } = await api();
  const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}`, 'Idempotency-Key': uuidv7() };
  const result = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers, body: { lessonId, message: 'hack the system' } });
  assert.equal(result.response.status, 201);
  assert.equal(result.payload.data.status, 'refused');
  assert.ok(result.payload.data.answer.includes('outside the scope'));
});

test('refused response is replayable', async () => {
  const { baseUrl } = await api();
  const key = uuidv7();
  const headers = { Authorization: `Bearer ${await accessToken(baseUrl)}`, 'Idempotency-Key': key };
  const body = { lessonId, message: 'hack the server' };
  const first = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers, body });
  assert.equal(first.response.status, 201);
  assert.equal(first.payload.data.status, 'refused');
  const replay = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers, body });
  assert.equal(replay.response.status, 200);
  assert.equal(replay.payload.data.answer, first.payload.data.answer);
});

// -- Concurrency tests --

test('active identical key returns activeProcessing 429', async () => {
  const repo = new MemoryIdentityRepository();
  const userId = 'test-user-active-dup';
  const idKey = uuidv7();
  const message = 'test';
  const { createHash } = await import('node:crypto');
  const fp = createHash('sha256').update(JSON.stringify({ lessonId, message })).digest('hex');

  repo.tutorRequests.push({
    id: uuidv7(), userId, idempotencyKey: idKey,
    requestFingerprint: fp, lessonId,
    normalizedResponse: null, state: 'processing',
    processingStartedAt: new Date(), claimToken: uuidv7(),
    createdAt: new Date(), completedAt: null,
    leaseExpiresAt: new Date(Date.now() + 30000),
    expiresAt: new Date(Date.now() + 86400000),
  });
  repo.tutorRequestsByKey.set(`${userId}:${idKey}`, 0);

  const rateAdmission = createRateAdmission({ burstLimit: 5, burstWindowMs: 60000, rollingLimit: 20, rollingWindowMs: 60000 });
  const fakeProvider = createFakeTutorProvider();
  const service = new AiTutorService({ repository: repo, rateAdmission, fakeProvider, config: { aiLeaseDurationSeconds: 30, aiConcurrencyLimit: 1 } });
  try {
    await service.generateTutorResponse({ userId, idempotencyKey: idKey, lessonId, message });
    assert.fail('Expected activeProcessing 429');
  } catch (error) {
    assert.equal(error.status, 429);
    assert.equal(error.code, 'rate_limit.exceeded');
  }
});

test('different key with active claim is rejected concurrency 429', async () => {
  const { baseUrl, repository } = await api({ concurrencyLimit: 1, leaseDurationSeconds: 30 });
  const auth = `Bearer ${await accessToken(baseUrl)}`;

  // First request � success
  const headers1 = { Authorization: auth, 'Idempotency-Key': uuidv7() };
  const first = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers: headers1, body: { lessonId, message: 'First claim' } });
  assert.equal(first.response.status, 201);

  // Manually insert an active processing row with different key to simulate concurrency
  const activeKey = uuidv7();
  const userId = repository.tutorRequests[0].userId;
  repository.tutorRequests.push({
    id: uuidv7(), userId, idempotencyKey: activeKey,
    requestFingerprint: 'ffff', lessonId,
    normalizedResponse: null, state: 'processing',
    processingStartedAt: new Date(), claimToken: uuidv7(),
    createdAt: new Date(), completedAt: null,
    leaseExpiresAt: new Date(Date.now() + 30000),
    expiresAt: new Date(Date.now() + 86400000),
  });
  repository.tutorRequestsByKey.set(`${userId}:${activeKey}`, repository.tutorRequests.length - 1);

  // Second request with different key should be concurrency-rejected
  const headers2 = { Authorization: auth, 'Idempotency-Key': uuidv7() };
  const rejection = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers: headers2, body: { lessonId, message: 'Second claim' } });
  assert.equal(rejection.response.status, 429);
  assert.equal(rejection.payload.error.code, 'rate_limit.exceeded');
});

test('stale key reclaimable only when no other active claim', async () => {
  const repo = new MemoryIdentityRepository();
  const userId = 'stale-test';
  const staleKey = uuidv7();
  const origClaimToken = uuidv7();

  // Insert a stale row (lease expired)
  repo.tutorRequests.push({
    id: uuidv7(), userId, idempotencyKey: staleKey,
    requestFingerprint: 'abc123', lessonId,
    normalizedResponse: null, state: 'processing',
    processingStartedAt: new Date(Date.now() - 60000), claimToken: origClaimToken,
    createdAt: new Date(Date.now() - 60000), completedAt: null,
    leaseExpiresAt: new Date(Date.now() - 30000), // expired 30 seconds ago
    expiresAt: new Date(Date.now() + 86400000),
  });
  repo.tutorRequestsByKey.set(`${userId}:${staleKey}`, 0);

  // With no other active claims, stale should be reclaimable
  const result = await repo.admitTutorProviderCall({
    userId, idempotencyKey: staleKey,
    requestFingerprint: 'abc123', lessonId,
    now: new Date(), concurrencyLimit: 1, leaseDurationSeconds: 30,
  });

  assert.equal(result.outcome, 'claimReclaimed');
  assert.notEqual(result.row.claimToken, origClaimToken);
});

test('stale key NOT reclaimed when another active claim exists', async () => {
  const repo = new MemoryIdentityRepository();
  const userId = 'stale-blocked';
  const staleKey = uuidv7();
  const activeKey = uuidv7();
  const origClaimToken = uuidv7();

  // Insert stale row
  repo.tutorRequests.push({
    id: uuidv7(), userId, idempotencyKey: staleKey,
    requestFingerprint: 'abc123', lessonId,
    normalizedResponse: null, state: 'processing',
    processingStartedAt: new Date(Date.now() - 60000), claimToken: origClaimToken,
    createdAt: new Date(Date.now() - 60000), completedAt: null,
    leaseExpiresAt: new Date(Date.now() - 30000),
    expiresAt: new Date(Date.now() + 86400000),
  });
  repo.tutorRequestsByKey.set(`${userId}:${staleKey}`, 0);

  // Insert ACTIVE row with different key
  repo.tutorRequests.push({
    id: uuidv7(), userId, idempotencyKey: activeKey,
    requestFingerprint: 'xyz789', lessonId,
    normalizedResponse: null, state: 'processing',
    processingStartedAt: new Date(), claimToken: uuidv7(),
    createdAt: new Date(), completedAt: null,
    leaseExpiresAt: new Date(Date.now() + 30000), // active
    expiresAt: new Date(Date.now() + 86400000),
  });
  repo.tutorRequestsByKey.set(`${userId}:${activeKey}`, 1);

  // Stale should be rejected because another active claim exists
  const result = await repo.admitTutorProviderCall({
    userId, idempotencyKey: staleKey,
    requestFingerprint: 'abc123', lessonId,
    now: new Date(), concurrencyLimit: 1, leaseDurationSeconds: 30,
  });

  assert.equal(result.outcome, 'concurrencyRejected');
  // Verify stale row was NOT mutated
  assert.equal(repo.tutorRequests[0].claimToken, origClaimToken);
});

// -- Rate-admission tests --

test('burst rejection does not consume counters', async () => {
  const repo = new MemoryIdentityRepository();
  const r0 = createRateAdmission({ burstLimit: 0, burstWindowMs: 60000, rollingLimit: 999, rollingWindowMs: 60000 });
  const fp = createFakeTutorProvider();
  const svc = new AiTutorService({ repository: repo, rateAdmission: r0, fakeProvider: fp, config: { aiLeaseDurationSeconds: 1, aiConcurrencyLimit: 1 } });

  try {
    await svc.generateTutorResponse({ userId: 'burst-test', idempotencyKey: uuidv7(), lessonId, message: 'test' });
    assert.fail('Expected burst rejection');
  } catch (error) {
    assert.equal(error.status, 429);
  }
  // No row created
  assert.equal(repo.tutorRequests.length, 0);
});

test('replay does not consume rate counters', async () => {
  const { baseUrl } = await api({ burstLimit: 1, burstWindowMs: 60000 });
  const key = uuidv7();
  const auth = `Bearer ${await accessToken(baseUrl)}`;
  const body = { lessonId, message: 'Rate counter test' };

  const first = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers: { Authorization: auth, 'Idempotency-Key': key }, body });
  assert.equal(first.response.status, 201);

  const replay = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers: { Authorization: auth, 'Idempotency-Key': key }, body });
  assert.equal(replay.response.status, 200);

  // New key with burstLimit=1 now exceeded should get 429
  const diff = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers: { Authorization: auth, 'Idempotency-Key': uuidv7() }, body: { lessonId, message: 'Should be rejected' } });
  assert.equal(diff.response.status, 429);
});

test('fingerprint conflict does not consume rate counters', async () => {
  const { baseUrl } = await api({ burstLimit: 1, burstWindowMs: 60000 });
  const key = uuidv7();
  const auth = `Bearer ${await accessToken(baseUrl)}`;

  const first = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers: { Authorization: auth, 'Idempotency-Key': key }, body: { lessonId, message: 'First' } });
  assert.equal(first.response.status, 201);

  const conflict = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers: { Authorization: auth, 'Idempotency-Key': key }, body: { lessonId, message: 'Different' } });
  assert.equal(conflict.response.status, 409);

  const out = await request(baseUrl, '/api/v1/ai/tutor-responses', { method: 'POST', headers: { Authorization: auth, 'Idempotency-Key': uuidv7() }, body: { lessonId, message: 'New' } });
  assert.equal(out.response.status, 429);
});

test('rolling rejection does not create a row', async () => {
  const repo = new MemoryIdentityRepository();
  // rollingLimit=0 means all requests are rolling-rejected
  const r0 = createRateAdmission({ burstLimit: 999, burstWindowMs: 60000, rollingLimit: 0, rollingWindowMs: 60000 });
  const fp = createFakeTutorProvider();
  const svc = new AiTutorService({ repository: repo, rateAdmission: r0, fakeProvider: fp, config: { aiLeaseDurationSeconds: 30, aiConcurrencyLimit: 1 } });

  try {
    await svc.generateTutorResponse({ userId: 'rolling-test', idempotencyKey: uuidv7(), lessonId, message: 'test' });
    assert.fail('Expected rolling rejection');
  } catch (error) {
    assert.equal(error.status, 429);
  }
  assert.equal(repo.tutorRequests.length, 0);
});

// -- Mutex tests --

test('three concurrent same-user requests serialize via mutex', async () => {
  const order = [];
  const userId = 'mutex-test-3';

  const results = await Promise.all([
    withTutorAdmissionLock(userId, async () => { order.push('r1-enter'); await new Promise(r => setTimeout(r, 10)); order.push('r1-exit'); return 'r1'; }),
    withTutorAdmissionLock(userId, async () => { order.push('r2-enter'); await new Promise(r => setTimeout(r, 10)); order.push('r2-exit'); return 'r2'; }),
    withTutorAdmissionLock(userId, async () => { order.push('r3-enter'); await new Promise(r => setTimeout(r, 10)); order.push('r3-exit'); return 'r3'; }),
  ]);

  assert.deepEqual(results, ['r1', 'r2', 'r3']);
  assert.deepEqual(order, [
    'r1-enter', 'r1-exit',
    'r2-enter', 'r2-exit',
    'r3-enter', 'r3-exit',
  ]);
});

test('five concurrent same-user requests serialize via mutex', async () => {
  const order = [];
  const userId = 'mutex-test-5';

  const ops = ['r1', 'r2', 'r3', 'r4', 'r5'].map((name) =>
    withTutorAdmissionLock(userId, async () => {
      order.push(`${name}-enter`);
      await new Promise((r) => setTimeout(r, 5));
      order.push(`${name}-exit`);
      return name;
    }),
  );

  const results = await Promise.all(ops);
  assert.deepEqual(results, ['r1', 'r2', 'r3', 'r4', 'r5']);
  for (let i = 0; i < 5; i++) {
    const e = order.indexOf(`r${i + 1}-enter`);
    const x = order.indexOf(`r${i + 1}-exit`);
    assert.ok(e < x);
    if (i < 4) assert.ok(x < order.indexOf(`r${i + 2}-enter`));
  }
});

test('middle request throws does not block subsequent requests', async () => {
  const order = [];
  const userId = 'mutex-throw';

  const r1 = withTutorAdmissionLock(userId, async () => { order.push('r1-enter'); order.push('r1-exit'); return 'r1-done'; });
  const r2 = withTutorAdmissionLock(userId, async () => { order.push('r2-enter'); throw new Error('mid crash'); });
  const r3 = withTutorAdmissionLock(userId, async () => { order.push('r3-enter'); order.push('r3-exit'); return 'r3-done'; });

  const results = await Promise.allSettled([r1, r2, r3]);
  assert.equal(results[0].status, 'fulfilled');
  assert.equal(results[0].value, 'r1-done');
  assert.equal(results[1].status, 'rejected');
  assert.equal(results[2].status, 'fulfilled');
  assert.equal(results[2].value, 'r3-done');
});

test('different users proceed independently', async () => {
  const events = [];
  const make = (userId, name) => withTutorAdmissionLock(userId, async () => {
    events.push(`${name}-start`);
    await new Promise((r) => setTimeout(r, 20));
    events.push(`${name}-end`);
    return name;
  });

  const results = await Promise.all([
    make('userA', 'ua1'),
    make('userB', 'ub1'),
    make('userA', 'ua2'),
    make('userB', 'ub2'),
  ]);

  assert.deepEqual(results, ['ua1', 'ub1', 'ua2', 'ub2']);
  const userAE = events.filter((e) => e.startsWith('ua'));
  const userBE = events.filter((e) => e.startsWith('ub'));
  assert.ok(userAE.indexOf('ua1-end') < userAE.indexOf('ua2-start'));
  assert.ok(userBE.indexOf('ub1-end') < userBE.indexOf('ub2-start'));
});

// -- Release, cleanup, and completion tests --

test('releaseTutorRequest deletes the processing row', async () => {
  const repo = new MemoryIdentityRepository();
  const userId = 'release-test';
  const idKey = uuidv7();
  const claimToken = uuidv7();

  repo.tutorRequests.push({
    id: uuidv7(), userId, idempotencyKey: idKey,
    requestFingerprint: 'fp', lessonId,
    normalizedResponse: null, state: 'processing',
    processingStartedAt: new Date(), claimToken,
    createdAt: new Date(), completedAt: null,
    leaseExpiresAt: new Date(Date.now() + 30000),
    expiresAt: new Date(Date.now() + 86400000),
  });
  repo.tutorRequestsByKey.set(`${userId}:${idKey}`, 0);
  assert.equal(repo.tutorRequests.length, 1);

  const result = await repo.releaseTutorRequest({ userId, idempotencyKey: idKey, claimToken });
  assert.deepEqual(result, { outcome: 'released' });
  assert.equal(repo.tutorRequests.length, 0);
});

test('releaseTutorRequest with wrong claimToken returns null', async () => {
  const repo = new MemoryIdentityRepository();
  const userId = 'wrong-claim';
  const idKey = uuidv7();

  repo.tutorRequests.push({
    id: uuidv7(), userId, idempotencyKey: idKey,
    requestFingerprint: 'fp', lessonId,
    normalizedResponse: null, state: 'processing',
    processingStartedAt: new Date(), claimToken: uuidv7(),
    createdAt: new Date(), completedAt: null,
    leaseExpiresAt: new Date(Date.now() + 30000),
    expiresAt: new Date(Date.now() + 86400000),
  });
  repo.tutorRequestsByKey.set(`${userId}:${idKey}`, 0);

  const result = await repo.releaseTutorRequest({ userId, idempotencyKey: idKey, claimToken: uuidv7() });
  assert.equal(result, null);
  assert.equal(repo.tutorRequests.length, 1);
});

test('deleteExpiredTutorRecords cleans completed and abandoned rows', async () => {
  const repo = new MemoryIdentityRepository();

  repo.tutorRequests.push({
    id: uuidv7(), userId: 'cleanup-1', idempotencyKey: uuidv7(),
    requestFingerprint: 'fp', lessonId,
    normalizedResponse: { answer: 'old' }, state: 'completed',
    processingStartedAt: new Date(Date.now() - 86400000), claimToken: uuidv7(),
    createdAt: new Date(Date.now() - 86400000), completedAt: new Date(Date.now() - 86400000),
    leaseExpiresAt: new Date(Date.now() - 86400000),
    expiresAt: new Date(Date.now() - 3600000),
  });
  repo.tutorRequestsByKey.set('cleanup-1', 0);

  repo.tutorRequests.push({
    id: uuidv7(), userId: 'cleanup-2', idempotencyKey: uuidv7(),
    requestFingerprint: 'fp', lessonId,
    normalizedResponse: null, state: 'processing',
    processingStartedAt: new Date(Date.now() - 600000), claimToken: uuidv7(),
    createdAt: new Date(Date.now() - 600000), completedAt: null,
    leaseExpiresAt: new Date(Date.now() - 400000),
    expiresAt: new Date(Date.now() + 86400000),
  });
  repo.tutorRequestsByKey.set('cleanup-2', 1);

  repo.tutorRequests.push({
    id: uuidv7(), userId: 'cleanup-3', idempotencyKey: uuidv7(),
    requestFingerprint: 'fp', lessonId,
    normalizedResponse: null, state: 'processing',
    processingStartedAt: new Date(), claimToken: uuidv7(),
    createdAt: new Date(), completedAt: null,
    leaseExpiresAt: new Date(Date.now() + 30000),
    expiresAt: new Date(Date.now() + 86400000),
  });
  repo.tutorRequestsByKey.set('cleanup-3', 2);

  assert.equal(repo.tutorRequests.length, 3);

  await repo.deleteExpiredTutorRecords(new Date());

  assert.equal(repo.tutorRequests.length, 1);
  assert.equal(repo.tutorRequests[0].userId, 'cleanup-3');
});

test('completeTutorRequest stores full TutorResponse', async () => {
  const repo = new MemoryIdentityRepository();
  const rowId = uuidv7();
  const userId = 'complete-test';
  const idKey = uuidv7();
  const claimToken = uuidv7();

  repo.tutorRequests.push({
    id: rowId, userId, idempotencyKey: idKey,
    requestFingerprint: 'fp', lessonId,
    normalizedResponse: null, state: 'processing',
    processingStartedAt: new Date(), claimToken,
    createdAt: new Date(), completedAt: null,
    leaseExpiresAt: new Date(Date.now() + 30000),
    expiresAt: new Date(Date.now() + 86400000),
  });
  repo.tutorRequestsByKey.set(`${userId}:${idKey}`, 0);

  const result = await repo.completeTutorRequest({
    userId, idempotencyKey: idKey, claimToken,
    answer: 'Here is guidance on greetings.', status: 'completed',
    now: new Date(),
  });

  assert.equal(result.outcome, 'completed');
  assert.equal(result.result.responseId, rowId);
  assert.equal(result.result.lessonId, lessonId);
  assert.equal(result.result.answer, 'Here is guidance on greetings.');
  assert.equal(result.result.status, 'completed');
  assert.ok(result.result.createdAt);

  const row = repo.tutorRequests[0];
  assert.equal(row.state, 'completed');
  assert.ok(row.completedAt);
  assert.ok(row.normalizedResponse);
  assert.equal(row.normalizedResponse.responseId, rowId);
});
