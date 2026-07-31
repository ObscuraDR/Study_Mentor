import assert from 'node:assert/strict';
import test, { afterEach } from 'node:test';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { MemoryIdentityRepository } from './support/memory-identity-repository.js';

const servers = new Set();
const logger = { info() {}, warn() {}, error() {} };
const idempotencyKey = '019f7e39-3000-7000-8000-000000000001';

afterEach(async () => {
  await Promise.all([...servers].map((server) => new Promise((resolve) => server.close(resolve))));
  servers.clear();
});

async function api({ eligibility = { eligible: true, missedLocalDate: '2026-03-09', policyVersion: 'streak-recovery-v1' }, claim = { status: 'accepted', replayed: false, missedLocalDate: '2026-03-09', policyVersion: 'streak-recovery-v1', acceptedAt: '2026-03-10T12:00:00.000Z' } } = {}) {
  const config = loadConfig({ environment: 'test', jwtAccessSecret: 'test-access-secret-that-is-at-least-thirty-two-characters', databaseUrl: 'postgres://test/test' });
  const repository = new MemoryIdentityRepository();
  const calls = [];
  const app = createApp({
    config, repository, logger, enableRateLimit: false,
    engagementService: { async streak() { return 4; }, async get() { return {}; } },
    streakRecoveryEligibilityService: { async get() { return eligibility; } },
    streakRecoveryClaimService: { async claim(input) { calls.push(input); return claim; }, },
  });
  const server = await new Promise((resolve) => { const current = app.listen(0, '127.0.0.1', () => resolve(current)); });
  servers.add(server);
  return { baseUrl: `http://127.0.0.1:${server.address().port}`, calls };
}

async function request(baseUrl, path, { method = 'GET', headers = {}, body } = {}) {
  const response = await fetch(`${baseUrl}${path}`, { method, headers: { ...headers, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, body: body === undefined ? undefined : JSON.stringify(body) });
  return { response, payload: await response.json() };
}

async function authorization(baseUrl) {
  const registered = await request(baseUrl, '/api/v1/auth/register', {
    method: 'POST', headers: { 'X-Client-Platform': 'android' },
    body: { displayName: 'Recovery Student', email: `recovery-${Date.now()}@example.com`, password: 'Correct Horse Battery 1' },
  });
  return `Bearer ${registered.payload.data.accessToken}`;
}

test('streak recovery endpoints require an authenticated session and return canonical request IDs', async () => {
  const { baseUrl } = await api();
  for (const [method, path] of [['GET', '/api/v1/me/streak-recovery'], ['POST', '/api/v1/me/streak-recoveries']]) {
    const result = await request(baseUrl, path, { method });
    assert.equal(result.response.status, 401);
    assert.equal(result.payload.error.code, 'auth.session_expired');
    assert.ok(result.payload.error.requestId);
    assert.equal(result.response.headers.get('x-request-id'), result.payload.error.requestId);
  }
});

test('eligible and ineligible eligibility reads expose only safe private projection fields', async () => {
  const eligibleApi = await api();
  const authorizationHeader = await authorization(eligibleApi.baseUrl);
  const eligible = await request(eligibleApi.baseUrl, '/api/v1/me/streak-recovery', { headers: { Authorization: authorizationHeader } });
  assert.equal(eligible.response.status, 200);
  assert.deepEqual(eligible.payload.data, { eligible: true, missedLocalDate: '2026-03-09', policyVersion: 'streak-recovery-v1', streak: 4 });
  assert.equal(eligible.payload.meta.requestId, eligible.response.headers.get('x-request-id'));
  assert.equal(JSON.stringify(eligible.payload.data).includes('qualifyingAction'), false);

  const ineligibleApi = await api({ eligibility: { eligible: false, reasonCode: 'prior_streak_too_short', policyVersion: 'streak-recovery-v1' } });
  const ineligible = await request(ineligibleApi.baseUrl, '/api/v1/me/streak-recovery', { headers: { Authorization: await authorization(ineligibleApi.baseUrl) } });
  assert.equal(ineligible.response.status, 200);
  assert.deepEqual(ineligible.payload.data, { eligible: false, reasonCode: 'prior_streak_too_short', policyVersion: 'streak-recovery-v1', streak: 4 });
  assert.equal(Object.hasOwn(ineligible.payload.data, 'missedLocalDate'), false);
});

test('claim requires an Idempotency-Key, rejects business fields, and delegates only authenticated identity and key', async () => {
  const instance = await api();
  const authorizationHeader = await authorization(instance.baseUrl);
  const missing = await request(instance.baseUrl, '/api/v1/me/streak-recoveries', { method: 'POST', headers: { Authorization: authorizationHeader } });
  assert.equal(missing.response.status, 422); assert.equal(missing.payload.error.code, 'validation.invalid_request');
  const bodyRejected = await request(instance.baseUrl, '/api/v1/me/streak-recoveries', { method: 'POST', headers: { Authorization: authorizationHeader, 'Idempotency-Key': idempotencyKey }, body: { missedLocalDate: '2026-03-09' } });
  assert.equal(bodyRejected.response.status, 422); assert.equal(bodyRejected.payload.error.code, 'validation.invalid_request');
  assert.equal(instance.calls.length, 0);

  const accepted = await request(instance.baseUrl, '/api/v1/me/streak-recoveries', { method: 'POST', headers: { Authorization: authorizationHeader, 'Idempotency-Key': idempotencyKey } });
  assert.equal(accepted.response.status, 201);
  assert.deepEqual(accepted.payload.data, { status: 'accepted', missedLocalDate: '2026-03-09', policyVersion: 'streak-recovery-v1', acceptedAt: '2026-03-10T12:00:00.000Z' });
  assert.equal(accepted.payload.meta.requestId, accepted.response.headers.get('x-request-id'));
  assert.deepEqual(Object.keys(instance.calls[0]).sort(), ['idempotencyKey', 'userId']);
});

test('idempotent replay retains the accepted result and ineligible claims map to a safe canonical conflict', async () => {
  const replayApi = await api({ claim: { status: 'accepted', replayed: true, missedLocalDate: '2026-03-09', policyVersion: 'streak-recovery-v1', acceptedAt: '2026-03-10T12:00:00.000Z' } });
  const replay = await request(replayApi.baseUrl, '/api/v1/me/streak-recoveries', { method: 'POST', headers: { Authorization: await authorization(replayApi.baseUrl), 'Idempotency-Key': idempotencyKey } });
  assert.equal(replay.response.status, 200); assert.equal(replay.response.headers.get('x-idempotent-replay'), 'true');
  assert.equal(Object.hasOwn(replay.payload.data, 'replayed'), false);

  const unavailableApi = await api({ claim: { status: 'ineligible', reasonCode: 'rolling_limit_reached', policyVersion: 'streak-recovery-v1' } });
  const unavailable = await request(unavailableApi.baseUrl, '/api/v1/me/streak-recoveries', { method: 'POST', headers: { Authorization: await authorization(unavailableApi.baseUrl), 'Idempotency-Key': idempotencyKey } });
  assert.equal(unavailable.response.status, 409);
  assert.equal(unavailable.payload.error.code, 'streak_recovery.ineligible');
  assert.deepEqual(unavailable.payload.error.details, [{ field: 'recovery', reason: 'rolling_limit_reached' }]);
  assert.equal(Object.hasOwn(unavailable.payload.error, 'sourceEvent'), false);
});
