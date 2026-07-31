import assert from 'node:assert/strict';
import test, { afterEach } from 'node:test';
import { createApp } from '../src/app.js';
import { isLoopbackRequest, loadConfig } from '../src/config.js';
import { MemoryIdentityRepository } from './support/memory-identity-repository.js';

const servers = new Set();
const silentLogger = { info() {}, warn() {}, error() {} };
const secret = 'test-access-secret-that-is-at-least-thirty-two-characters';

afterEach(async () => {
  await Promise.all([...servers].map((server) => new Promise((resolve) => server.close(resolve))));
  servers.clear();
});

async function api(configOverrides = {}) {
  const config = loadConfig({ environment: 'test', jwtAccessSecret: secret, databaseUrl: 'postgres://test/test', ...configOverrides });
  const repository = new MemoryIdentityRepository();
  const app = createApp({ config, repository, logger: silentLogger, enableRateLimit: false });
  const server = await new Promise((resolve) => { const current = app.listen(0, '127.0.0.1', () => resolve(current)); });
  servers.add(server);
  return { baseUrl: `http://127.0.0.1:${server.address().port}`, repository, config };
}

async function request(baseUrl, path, { method = 'GET', headers = {}, body } = {}) {
  const response = await fetch(`${baseUrl}${path}`, { method, headers: { ...headers, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, body: body === undefined ? undefined : JSON.stringify(body) });
  return { response, payload: response.status === 204 ? undefined : await response.json() };
}

async function register(baseUrl, platform = 'android') {
  return request(baseUrl, '/api/v1/auth/register', { method: 'POST', headers: { 'X-Client-Platform': platform, ...(platform === 'web' ? { Origin: 'http://localhost:3000' } : {}) }, body: { displayName: 'Settings Student', email: `settings-${Math.random()}@example.com`, password: 'Correct Horse Battery 1' } });
}

test('registration creates contract defaults and authenticated settings retrieval returns an ETag', async () => {
  const { baseUrl, repository } = await api();
  const session = (await register(baseUrl)).payload.data;
  const current = await request(baseUrl, '/api/v1/me/settings', { headers: { Authorization: `Bearer ${session.accessToken}` } });
  assert.equal(current.response.status, 200);
  assert.deepEqual(Object.keys(current.payload.data).sort(), ['dailyGoalTargetXp', 'locale', 'revision', 'updatedAt']);
  assert.equal(current.payload.data.locale, 'vi'); assert.equal(current.payload.data.dailyGoalTargetXp, 300);
  assert.equal(current.response.headers.get('etag'), current.payload.data.revision);
  assert.equal((await repository.findSettings(session.user.id)).dailyGoalTargetXp, 300);
  const denied = await request(baseUrl, '/api/v1/me/settings');
  assert.equal(denied.response.status, 401);
});

test('settings replacement validates the complete contract and never accepts device-only fields', async () => {
  const { baseUrl } = await api();
  const session = (await register(baseUrl)).payload.data;
  const headers = { Authorization: `Bearer ${session.accessToken}` };
  const current = await request(baseUrl, '/api/v1/me/settings', { headers });
  const updated = await request(baseUrl, '/api/v1/me/settings', { method: 'PUT', headers: { ...headers, 'If-Match': current.payload.data.revision }, body: { locale: 'en', dailyGoalTargetXp: 750 } });
  assert.equal(updated.response.status, 200); assert.equal(updated.payload.data.locale, 'en'); assert.equal(updated.payload.data.dailyGoalTargetXp, 750);
  assert.equal(updated.response.headers.get('etag'), updated.payload.data.revision); assert.notEqual(updated.payload.data.revision, current.payload.data.revision);
  for (const body of [{ locale: 'fr', dailyGoalTargetXp: 10 }, { locale: 'vi', dailyGoalTargetXp: 0 }, { locale: 'vi', dailyGoalTargetXp: 10_001 }, { locale: 'vi', dailyGoalTargetXp: 10, theme: 'dark' }]) {
    const invalid = await request(baseUrl, '/api/v1/me/settings', { method: 'PUT', headers: { ...headers, 'If-Match': updated.payload.data.revision }, body });
    assert.equal(invalid.response.status, 422); assert.equal(invalid.payload.error.code, 'validation.invalid_request');
  }
});

test('settings revisions permit exactly one concurrent update and reject stale writes', async () => {
  const { baseUrl } = await api();
  const session = (await register(baseUrl)).payload.data;
  const headers = { Authorization: `Bearer ${session.accessToken}` };
  const current = await request(baseUrl, '/api/v1/me/settings', { headers });
  const [left, right] = await Promise.all([
    request(baseUrl, '/api/v1/me/settings', { method: 'PUT', headers: { ...headers, 'If-Match': current.payload.data.revision }, body: { locale: 'vi', dailyGoalTargetXp: 301 } }),
    request(baseUrl, '/api/v1/me/settings', { method: 'PUT', headers: { ...headers, 'If-Match': current.payload.data.revision }, body: { locale: 'en', dailyGoalTargetXp: 302 } }),
  ]);
  assert.equal([left, right].filter((result) => result.response.status === 200).length, 1);
  const conflict = [left, right].find((result) => result.response.status === 409);
  assert.equal(conflict.payload.error.code, 'conflict.revision_mismatch');
});

test('cookie security configuration rejects unsafe production and permits only the explicit loopback development exception', () => {
  assert.throws(() => loadConfig({ environment: 'production', jwtAccessSecret: secret, databaseUrl: 'postgres://test/test', refreshCookieSecure: false, allowInsecureLoopbackRefreshCookie: true, webOrigins: 'http://localhost:3000' }), /Production and staging/u);
  assert.throws(() => loadConfig({ environment: 'development', jwtAccessSecret: secret, databaseUrl: 'postgres://test/test', refreshCookieSecure: false, allowInsecureLoopbackRefreshCookie: false, webOrigins: 'http://localhost:3000' }), /loopback exception/u);
  assert.throws(() => loadConfig({ environment: 'development', jwtAccessSecret: secret, databaseUrl: 'postgres://test/test', refreshCookieSecure: false, allowInsecureLoopbackRefreshCookie: true, webOrigins: 'http://example.test:3000' }), /loopback WEB_ORIGINS/u);
  const development = loadConfig({ environment: 'development', jwtAccessSecret: secret, databaseUrl: 'postgres://test/test', refreshCookieSecure: false, allowInsecureLoopbackRefreshCookie: true, webOrigins: 'http://localhost:3000' });
  assert.equal(development.refreshCookieSecure, false); assert.equal(development.allowInsecureLoopbackRefreshCookie, true);
});

test('development loopback cookies retain HttpOnly and SameSite while non-loopback requests are never eligible', async () => {
  const { baseUrl } = await api({ environment: 'test', refreshCookieSecure: false, allowInsecureLoopbackRefreshCookie: true, webOrigins: 'http://localhost:3000' });
  const registered = await register(baseUrl, 'web');
  assert.equal(registered.response.status, 201);
  const cookie = registered.response.headers.get('set-cookie');
  assert.doesNotMatch(cookie, /Secure/u); assert.match(cookie, /HttpOnly/u); assert.match(cookie, /SameSite=Lax/u);
  assert.equal(isLoopbackRequest({ hostname: 'localhost' }), true);
  assert.equal(isLoopbackRequest({ hostname: 'api.example.test' }), false);
});

test('cookie-authenticated Web operations enforce exact allowed Origin with no session mutation on rejection', async () => {
  const { baseUrl } = await api();
  const registered = await register(baseUrl, 'web');
  const session = registered.payload.data;
  const cookie = registered.response.headers.get('set-cookie').split(';')[0];
  const deniedRefresh = await request(baseUrl, '/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'web', Origin: 'http://localhost:3000.evil.test', Cookie: cookie } });
  assert.equal(deniedRefresh.response.status, 403);
  const allowedRefresh = await request(baseUrl, '/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'web', Origin: 'http://localhost:3000', Cookie: cookie } });
  assert.equal(allowedRefresh.response.status, 200);
  const deniedLogout = await request(baseUrl, '/api/v1/auth/logout', { method: 'POST', headers: { Authorization: `Bearer ${session.accessToken}`, 'X-Client-Platform': 'web', Origin: 'http://localhost:3000.evil.test' } });
  assert.equal(deniedLogout.response.status, 403);
  const deniedLogoutAll = await request(baseUrl, '/api/v1/auth/logout-all', { method: 'POST', headers: { Authorization: `Bearer ${session.accessToken}`, 'X-Client-Platform': 'web', Origin: 'http://localhost:3000.evil.test' } });
  assert.equal(deniedLogoutAll.response.status, 403);
  const stillActive = await request(baseUrl, '/api/v1/auth/me', { headers: { Authorization: `Bearer ${session.accessToken}` } });
  assert.equal(stillActive.response.status, 200);
  const missingWebOrigin = await request(baseUrl, '/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'web', Cookie: allowedRefresh.response.headers.get('set-cookie').split(';')[0] } });
  assert.equal(missingWebOrigin.response.status, 403);
  const allowedLogout = await request(baseUrl, '/api/v1/auth/logout', { method: 'POST', headers: { Authorization: `Bearer ${session.accessToken}`, 'X-Client-Platform': 'web', Origin: 'http://localhost:3000' } });
  assert.equal(allowedLogout.response.status, 204);
  assert.match(allowedLogout.response.headers.get('set-cookie'), /Path=\/api\/v1\/auth/u);
  assert.match(allowedLogout.response.headers.get('set-cookie'), /HttpOnly/u);
  assert.match(allowedLogout.response.headers.get('set-cookie'), /Secure/u);
  assert.match(allowedLogout.response.headers.get('set-cookie'), /SameSite=Lax/u);
});

test('native Android refresh remains available without a browser Origin', async () => {
  const { baseUrl } = await api();
  const session = (await register(baseUrl, 'android')).payload.data;
  const refreshed = await request(baseUrl, '/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { refreshToken: session.refreshToken } });
  assert.equal(refreshed.response.status, 200);
});
