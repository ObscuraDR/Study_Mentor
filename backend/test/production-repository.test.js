import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const repositorySource = await readFile(new URL('../src/repositories/postgres-identity-repository.js', import.meta.url), 'utf8');
const appSource = await readFile(new URL('../src/app.js', import.meta.url), 'utf8');

test('production identity implementation uses PostgreSQL and no JSON-file or bcrypt identity adapters', () => {
  assert.match(repositorySource, /import \{ Pool \} from 'pg'/u);
  assert.match(repositorySource, /rotateRefreshToken/u);
  assert.match(repositorySource, /FOR UPDATE/u);
  assert.doesNotMatch(repositorySource, /users\.json|bcrypt/u);
  assert.doesNotMatch(appSource, /store\.js|generateRefreshToken|verifyRefreshToken/u);
});

test('the application creates opaque cookie and Android refresh transports without logging tokens', () => {
  assert.match(appSource, /httpOnly: true,/u);
  assert.match(appSource, /secure: config\.refreshCookieSecure,/u);
  assert.match(appSource, /sameSite: 'lax'/u);
  assert.match(appSource, /sessionData\(session, platform\)/u);
  assert.doesNotMatch(appSource, /logger\.(?:info|warn|error)\([^\n]*(?:refreshToken|password|accessToken)/u);
});
