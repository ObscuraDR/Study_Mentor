import assert from 'node:assert/strict';
import test, { afterEach } from 'node:test';
import jwt from 'jsonwebtoken';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { MemoryIdentityRepository } from './support/memory-identity-repository.js';

const servers = new Set();
const silentLogger = { info() {}, warn() {}, error() {} };

afterEach(async () => {
  await Promise.all([...servers].map((server) => new Promise((resolve) => server.close(resolve))));
  servers.clear();
});

async function api() {
  const config = loadConfig({ environment: 'test', jwtAccessSecret: 'test-access-secret-that-is-at-least-thirty-two-characters', databaseUrl: 'postgres://test/test' });
  const repository = new MemoryIdentityRepository();
  const app = createApp({ config, repository, logger: silentLogger, enableRateLimit: false });
  const server = await new Promise((resolve) => { const current = app.listen(0, '127.0.0.1', () => resolve(current)); });
  servers.add(server);
  const { port } = server.address();
  return { baseUrl: `http://127.0.0.1:${port}`, repository, config };
}

async function request(baseUrl, path, { method = 'GET', headers = {}, body } = {}) {
  const response = await fetch(`${baseUrl}${path}`, { method, headers: { ...headers, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, body: body === undefined ? undefined : JSON.stringify(body) });
  const payload = response.status === 204 ? undefined : await response.json();
  return { response, payload };
}

async function register(baseUrl, platform = 'android', email = 'student@example.com') {
  return request(baseUrl, '/api/v1/auth/register', { method: 'POST', headers: { 'X-Client-Platform': platform }, body: { displayName: 'Student', email, password: 'Correct Horse Battery 1' } });
}

test('registration persists an Argon2id password hash and returns the Android contract session', async () => {
  const { baseUrl, repository } = await api();
  const { response, payload } = await register(baseUrl);
  assert.equal(response.status, 201);
  assert.match(payload.data.user.id, /^[0-9a-f]{8}-[0-9a-f]{4}-7/u);
  assert.ok(payload.data.accessToken);
  assert.ok(payload.data.refreshToken);
  assert.equal(payload.data.password, undefined);
  assert.match((await repository.findUserByEmail('student@example.com')).passwordHash, /^\$argon2id\$/u);
  assert.equal(JSON.stringify(repository).includes('Correct Horse Battery 1'), false);
});

test('registration validates the contract body and duplicate email', async () => {
  const { baseUrl } = await api();
  let result = await request(baseUrl, '/api/v1/auth/register', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { email: 'invalid', password: 'Correct Horse Battery 1' } });
  assert.equal(result.response.status, 422); assert.equal(result.payload.error.code, 'validation.invalid_request');
  await register(baseUrl);
  result = await register(baseUrl);
  assert.equal(result.response.status, 409); assert.equal(result.payload.error.code, 'auth.email_already_registered');
});

test('login authenticates valid credentials and rejects invalid credentials', async () => {
  const { baseUrl } = await api();
  await register(baseUrl);
  const valid = await request(baseUrl, '/api/v1/auth/login', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { email: 'student@example.com', password: 'Correct Horse Battery 1' } });
  assert.equal(valid.response.status, 200); assert.ok(valid.payload.data.accessToken);
  const invalid = await request(baseUrl, '/api/v1/auth/login', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { email: 'student@example.com', password: 'wrong password' } });
  assert.equal(invalid.response.status, 401); assert.equal(invalid.payload.error.code, 'auth.invalid_credentials');
});

test('web registration and refresh use Secure HttpOnly cookies and never expose JSON refresh tokens', async () => {
  const { baseUrl } = await api();
  const registered = await register(baseUrl, 'web');
  assert.equal(registered.response.status, 201);
  assert.equal(registered.payload.data.refreshToken, undefined);
  const setCookie = registered.response.headers.get('set-cookie');
  assert.match(setCookie, /asm_refresh=/u); assert.match(setCookie, /HttpOnly/u); assert.match(setCookie, /Secure/u); assert.match(setCookie, /SameSite=Lax/u);
  const refreshed = await request(baseUrl, '/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'web', Origin: 'http://localhost:3000', Cookie: setCookie.split(';')[0] } });
  assert.equal(refreshed.response.status, 200); assert.equal(refreshed.payload.data.refreshToken, undefined);
  const bodyRejected = await request(baseUrl, '/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'web', Origin: 'http://localhost:3000', Cookie: refreshed.response.headers.get('set-cookie').split(';')[0] }, body: { refreshToken: 'must-not-be-used' } });
  assert.equal(bodyRejected.response.status, 422); assert.equal(bodyRejected.payload.error.code, 'validation.invalid_request');
});

test('Android refresh rotates a token and detects reuse by revoking its family', async () => {
  const { baseUrl } = await api();
  const original = (await register(baseUrl)).payload.data.refreshToken;
  const rotated = await request(baseUrl, '/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { refreshToken: original } });
  assert.equal(rotated.response.status, 200); assert.notEqual(rotated.payload.data.refreshToken, original);
  assert.match(rotated.payload.data.refreshTokenExpiresAt, /Z$/u);
  const reused = await request(baseUrl, '/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { refreshToken: original } });
  assert.equal(reused.response.status, 401); assert.equal(reused.payload.error.code, 'auth.refresh_token_reused');
  const revoked = await request(baseUrl, '/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { refreshToken: rotated.payload.data.refreshToken } });
  assert.equal(revoked.response.status, 401); assert.equal(revoked.payload.error.code, 'auth.session_revoked');
});

test('invalid refresh payloads and tokens are rejected without server errors', async () => {
  const { baseUrl } = await api();
  const missing = await request(baseUrl, '/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: {} });
  assert.equal(missing.response.status, 422);
  const invalid = await request(baseUrl, '/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { refreshToken: 'invalid' } });
  assert.equal(invalid.response.status, 401); assert.equal(invalid.payload.error.code, 'auth.refresh_token_invalid');
});

test('logout revokes the current family and invalidates its access token immediately', async () => {
  const { baseUrl } = await api();
  const session = (await register(baseUrl)).payload.data;
  const logout = await request(baseUrl, '/api/v1/auth/logout', { method: 'POST', headers: { Authorization: `Bearer ${session.accessToken}`, 'X-Client-Platform': 'android' } });
  assert.equal(logout.response.status, 204);
  const currentUser = await request(baseUrl, '/api/v1/auth/me', { headers: { Authorization: `Bearer ${session.accessToken}` } });
  assert.equal(currentUser.response.status, 401); assert.equal(currentUser.payload.error.code, 'auth.session_revoked');
});

test('logout requires the contract client-platform header', async () => {
  const { baseUrl } = await api();
  const session = (await register(baseUrl)).payload.data;
  const logout = await request(baseUrl, '/api/v1/auth/logout', { method: 'POST', headers: { Authorization: `Bearer ${session.accessToken}` } });
  assert.equal(logout.response.status, 422); assert.equal(logout.payload.error.code, 'validation.invalid_request');
});

test('logout all revokes every active user session', async () => {
  const { baseUrl } = await api();
  const first = (await register(baseUrl)).payload.data;
  const second = (await request(baseUrl, '/api/v1/auth/login', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { email: 'student@example.com', password: 'Correct Horse Battery 1' } })).payload.data;
  const logout = await request(baseUrl, '/api/v1/auth/logout-all', { method: 'POST', headers: { Authorization: `Bearer ${first.accessToken}`, 'X-Client-Platform': 'android' } });
  assert.equal(logout.response.status, 204);
  const denied = await request(baseUrl, '/api/v1/auth/me', { headers: { Authorization: `Bearer ${second.accessToken}` } });
  assert.equal(denied.response.status, 401); assert.equal(denied.payload.error.code, 'auth.session_revoked');
});

test('protected routes reject missing and expired access tokens', async () => {
  const { baseUrl, config } = await api();
  let result = await request(baseUrl, '/api/v1/auth/me');
  assert.equal(result.response.status, 401);
  const expired = jwt.sign({ sub: '00000000-0000-7000-8000-000000000000', fid: '00000000-0000-7000-8000-000000000000', role: 'user' }, config.jwtAccessSecret, { algorithm: 'HS256', issuer: config.jwtIssuer, audience: config.jwtAudience, expiresIn: -1 });
  result = await request(baseUrl, '/api/v1/auth/me', { headers: { Authorization: `Bearer ${expired}` } });
  assert.equal(result.response.status, 401); assert.equal(result.payload.error.code, 'auth.session_expired');
});

test('profile retrieval and revision-protected update follow the contract', async () => {
  const { baseUrl } = await api();
  const session = (await register(baseUrl)).payload.data;
  const headers = { Authorization: `Bearer ${session.accessToken}` };
  const current = await request(baseUrl, '/api/v1/me/profile', { headers });
  assert.equal(current.response.status, 200); assert.equal(current.response.headers.get('etag'), current.payload.data.revision);
  const updated = await request(baseUrl, '/api/v1/me/profile', { method: 'PATCH', headers: { ...headers, 'If-Match': current.payload.data.revision }, body: { displayName: 'Updated Student', educationLevel: 'advanced' } });
  assert.equal(updated.response.status, 200); assert.equal(updated.payload.data.displayName, 'Updated Student'); assert.equal(updated.payload.data.educationLevel, 'advanced');
  const conflict = await request(baseUrl, '/api/v1/me/profile', { method: 'PATCH', headers: { ...headers, 'If-Match': current.payload.data.revision }, body: { avatarKey: 'new-avatar' } });
  assert.equal(conflict.response.status, 409); assert.equal(conflict.payload.error.code, 'conflict.revision_mismatch');
});

test('responses include matching correlation IDs in headers and envelopes', async () => {
  const { baseUrl } = await api();
  const result = await register(baseUrl);
  assert.equal(result.response.headers.get('x-request-id'), result.payload.meta.requestId);
  const invalid = await request(baseUrl, '/api/v1/auth/login', { method: 'POST', headers: { 'X-Client-Platform': 'invalid' }, body: { email: 'student@example.com', password: 'password' } });
  assert.equal(invalid.response.headers.get('x-request-id'), invalid.payload.error.requestId);
});
