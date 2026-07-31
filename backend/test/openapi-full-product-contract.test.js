import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const contract = JSON.parse(await readFile(
  new URL('../../contracts/openapi/ai-study-mentor.v1.openapi.json', import.meta.url),
  'utf8',
));

test('OpenAPI exposes authenticated Full Product v1 endpoints', () => {
  for (const [path, method] of [
    ['/me/wrong-answers', 'get'],
    ['/me/boss-challenge', 'get'],
    ['/boss-attempts', 'post'],
    ['/me/economy', 'get'],
    ['/me/shop-purchases', 'post'],
  ]) {
    assert.deepEqual(contract.paths[path][method].security, [{ bearerAuth: [] }]);
  }
});

test('boss questions cannot expose correctness and writes accept selections only', () => {
  const option = contract.components.schemas.BossOption;
  assert.equal(Object.hasOwn(option.properties, 'correct'), false);
  assert.deepEqual(Object.keys(contract.components.schemas.BossAttemptRequest.properties).sort(), ['answers', 'challengeId']);
  assert.deepEqual(Object.keys(contract.components.schemas.BossAnswerRequest.properties).sort(), ['questionId', 'selectedOptionId']);
  assert.equal(contract.paths['/boss-attempts'].post.parameters[0].$ref, '#/components/parameters/IdempotencyKey');
});

test('economy writes cannot set price balance reward or inventory', () => {
  assert.deepEqual(Object.keys(contract.components.schemas.PurchaseRequest.properties), ['itemId']);
  assert.equal(contract.components.schemas.EconomyProjection.properties.currency.enum[0], 'shell');
  assert.equal(contract.paths['/me/shop-purchases'].post.parameters[0].$ref, '#/components/parameters/IdempotencyKey');
});
