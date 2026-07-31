import assert from 'node:assert/strict';
import test from 'node:test';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { MemoryIdentityRepository } from './support/memory-identity-repository.js';

test('campaign route requires authentication and returns canonical private envelope', async () => {
  const repository = new MemoryIdentityRepository();
  const config = loadConfig({ environment: 'test', jwtAccessSecret: 'test-access-secret-that-is-at-least-thirty-two-characters', databaseUrl: 'postgres://test/test' });
  const app = createApp({ config, repository, enableRateLimit: false, logger: { info() {}, warn() {}, error() {} } });
  const server = await new Promise((resolve) => { const current = app.listen(0, '127.0.0.1', () => resolve(current)); });
  try {
    const response = await fetch(`http://127.0.0.1:${server.address().port}/api/v1/me/campaign`);
    const body = await response.json();
    assert.equal(response.status, 401);
    assert.equal(body.error.code, 'auth.session_expired');
    assert.match(body.error.requestId, /^req_/);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});

test('authenticated campaign response is private, ordered, and server-derived', async () => {
  const repository = new MemoryIdentityRepository();
  const config = loadConfig({ environment: 'test', jwtAccessSecret: 'test-access-secret-that-is-at-least-thirty-two-characters', databaseUrl: 'postgres://test/test' });
  const app = createApp({ config, repository, enableRateLimit: false, logger: { info() {}, warn() {}, error() {} } });
  const server = await new Promise((resolve) => { const current = app.listen(0, '127.0.0.1', () => resolve(current)); });
  try {
    const base = `http://127.0.0.1:${server.address().port}`;
    const registered = await fetch(`${base}/api/v1/auth/register`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Client-Platform': 'android' },
      body: JSON.stringify({ displayName: 'Campaign test', email: 'campaign-test@example.com', password: 'Correct Horse Battery 1' }),
    });
    const session = await registered.json();
    const response = await fetch(`${base}/api/v1/me/campaign`, { headers: { Authorization: `Bearer ${session.data.accessToken}` } });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.match(body.meta.requestId, /^req_/);
    assert.equal(body.data.campaignKey, 'core-learning-map');
    assert.equal(body.data.zones[0].subjectId, repository.subjects[0].id);
    assert.equal(body.data.recommendedNodeId, repository.lessons[0].id);
    assert.equal(Object.prototype.hasOwnProperty.call(body.data, 'xp'), false);
    assert.equal(Object.prototype.hasOwnProperty.call(body.data, 'otherLearners'), false);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
});
