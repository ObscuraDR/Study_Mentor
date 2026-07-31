import assert from 'node:assert/strict';
import test from 'node:test';
import { loadConfig } from '../src/config.js';

const secret = 'local-development-only-access-secret-at-least-32-characters';

test('configuration rejects missing database URLs outside tests and malformed values', () => {
  assert.throws(() => loadConfig({ environment: 'production', jwtAccessSecret: secret, databaseUrl: '' }), /DATABASE_URL must be configured/u);
  assert.throws(() => loadConfig({ environment: 'production', jwtAccessSecret: secret, databaseUrl: 'not-a-url' }), /valid PostgreSQL/u);
  assert.throws(() => loadConfig({ environment: 'production', jwtAccessSecret: secret, databaseUrl: 'https://example.com/database' }), /PostgreSQL URL/u);
});

test('configuration rejects invalid ports and accepts an isolated test database URL', () => {
  assert.throws(() => loadConfig({ environment: 'test', jwtAccessSecret: secret, databaseUrl: 'postgres://user:pass@127.0.0.1:54329/database', port: '0' }), /PORT/u);
  const config = loadConfig({ environment: 'test', jwtAccessSecret: secret, databaseUrl: 'postgres://user:pass@127.0.0.1:54329/database_test', port: 8081 });
  assert.equal(config.port, 8081);
  assert.equal(config.databaseUrl.endsWith('database_test'), true);
});
