import assert from 'node:assert/strict';
import test, { afterEach } from 'node:test';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { MemoryIdentityRepository } from './support/memory-identity-repository.js';

const servers = new Set();
const silentLogger = { info() {}, warn() {}, error() {} };
const ids = {
  quiz: '019f7e39-0006-7000-8000-000000000001',
  quizQ1: '019f7e39-0007-7000-8000-000000000001',
  quizQ2: '019f7e39-0008-7000-8000-000000000001',
  quizWrong1: '019f7e39-0010-7000-8000-000000000001',
  quizCorrect2: '019f7e39-0013-7000-8000-000000000001',
  boss: '019f7e39-0200-7000-8000-000000000001',
  bossQ1: '019f7e39-0201-7000-8000-000000000001',
  bossQ2: '019f7e39-0202-7000-8000-000000000001',
  bossCorrect1: '019f7e39-0203-7000-8000-000000000001',
  bossCorrect2: '019f7e39-0206-7000-8000-000000000001',
  shopItem: '019f7e39-0210-7000-8000-000000000001',
};

afterEach(async () => {
  await Promise.all([...servers].map((server) => new Promise((resolve) => server.close(resolve))));
  servers.clear();
});

async function request(baseUrl, path, { method = 'GET', headers = {}, body } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: { ...headers, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return { response, payload: response.status === 204 ? undefined : await response.json() };
}

async function api() {
  const config = loadConfig({ environment: 'test', jwtAccessSecret: 'test-access-secret-that-is-at-least-thirty-two-characters', databaseUrl: 'postgres://test/test' });
  const repository = new MemoryIdentityRepository();
  const app = createApp({ config, repository, logger: silentLogger, enableRateLimit: false });
  const server = await new Promise((resolve) => {
    const current = app.listen(0, '127.0.0.1', () => resolve(current));
  });
  servers.add(server);
  const baseUrl = `http://127.0.0.1:${server.address().port}`;
  const registered = await request(baseUrl, '/api/v1/auth/register', {
    method: 'POST', headers: { 'X-Client-Platform': 'android' },
    body: { displayName: 'Full Product Learner', email: 'full-product@example.com', password: 'Correct Horse Battery 1' },
  });
  return { baseUrl, headers: { Authorization: `Bearer ${registered.payload.data.accessToken}` }, repository };
}

function bossBody() {
  return {
    challengeId: ids.boss,
    answers: [
      { questionId: ids.bossQ1, selectedOptionId: ids.bossCorrect1 },
      { questionId: ids.bossQ2, selectedOptionId: ids.bossCorrect2 },
    ],
  };
}

test('wrong-answer history is private and derived from accepted attempts', async () => {
  const { baseUrl, headers } = await api();
  await request(baseUrl, '/api/v1/quiz-attempts', {
    method: 'POST',
    headers: { ...headers, 'Idempotency-Key': '019f7e39-0300-7000-8000-000000000001' },
    body: {
      quizId: ids.quiz,
      answers: [
        { questionId: ids.quizQ1, selectedOptionId: ids.quizWrong1 },
        { questionId: ids.quizQ2, selectedOptionId: ids.quizCorrect2 },
      ],
    },
  });
  const history = await request(baseUrl, '/api/v1/me/wrong-answers?page=1&pageSize=20', { headers });
  assert.equal(history.response.status, 200);
  assert.equal(history.payload.data.totalItems, 1);
  assert.equal(history.payload.data.items[0].correctOptionText, 'Good morning');
});

test('boss awards shells once and never exposes an answer key', async () => {
  const { baseUrl, headers, repository } = await api();
  const challenge = await request(baseUrl, '/api/v1/me/boss-challenge', { headers });
  assert.equal(challenge.response.status, 200);
  assert.equal(JSON.stringify(challenge.payload.data).includes('"correct"'), false);
  const attemptHeaders = { ...headers, 'Idempotency-Key': '019f7e39-0301-7000-8000-000000000001' };
  const first = await request(baseUrl, '/api/v1/boss-attempts', { method: 'POST', headers: attemptHeaders, body: bossBody() });
  assert.equal(first.response.status, 201);
  assert.equal(first.payload.data.rewardShells, 25);
  const replay = await request(baseUrl, '/api/v1/boss-attempts', { method: 'POST', headers: attemptHeaders, body: bossBody() });
  assert.equal(replay.response.status, 200);
  assert.equal(repository.wallets.values().next().value, 25);
});

test('cosmetic purchase is atomic and reflected in inventory', async () => {
  const { baseUrl, headers } = await api();
  await request(baseUrl, '/api/v1/boss-attempts', {
    method: 'POST',
    headers: { ...headers, 'Idempotency-Key': '019f7e39-0302-7000-8000-000000000001' },
    body: bossBody(),
  });
  const purchaseHeaders = { ...headers, 'Idempotency-Key': '019f7e39-0303-7000-8000-000000000001' };
  const first = await request(baseUrl, '/api/v1/me/shop-purchases', {
    method: 'POST', headers: purchaseHeaders, body: { itemId: ids.shopItem },
  });
  assert.equal(first.response.status, 201);
  assert.equal(first.payload.data.balance, 5);
  const replay = await request(baseUrl, '/api/v1/me/shop-purchases', {
    method: 'POST', headers: purchaseHeaders, body: { itemId: ids.shopItem },
  });
  assert.equal(replay.response.status, 200);
  assert.equal(replay.payload.data.purchaseId, first.payload.data.purchaseId);
  const economy = await request(baseUrl, '/api/v1/me/economy', { headers });
  assert.equal(economy.payload.data.shopItems[0].owned, true);
  assert.equal(economy.payload.data.inventory[0].quantity, 1);
});
