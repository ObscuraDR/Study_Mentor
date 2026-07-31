import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const contract = JSON.parse(await readFile(new URL('../../contracts/openapi/ai-study-mentor.v1.openapi.json', import.meta.url), 'utf8'));

test('OpenAPI defines authenticated private streak recovery read and idempotent claim operations', () => {
  const read = contract.paths['/me/streak-recovery'].get;
  const claim = contract.paths['/me/streak-recoveries'].post;
  assert.deepEqual(read.security, [{ bearerAuth: [] }]);
  assert.deepEqual(Object.keys(read.responses).sort(), ['200', '401']);
  assert.deepEqual(claim.security, [{ bearerAuth: [] }]);
  assert.equal(claim.requestBody, undefined);
  assert.ok(claim.parameters.some((parameter) => parameter.$ref === '#/components/parameters/IdempotencyKey'));
  assert.deepEqual(Object.keys(claim.responses).sort(), ['200', '201', '401', '409', '422']);
  for (const responseName of ['StreakRecoveryEligibility', 'StreakRecoveryClaimCreated', 'StreakRecoveryClaimReplay', 'StreakRecoveryUnavailable']) {
    assert.ok(contract.components.responses[responseName].content['application/json'].examples);
  }
});

test('streak recovery schemas are safe private envelopes with no client-controlled outcome fields', () => {
  const eligibility = contract.components.schemas.StreakRecoveryEligibilityProjection;
  assert.equal(eligibility.oneOf.length, 2);
  const [eligible, ineligible] = eligibility.oneOf;
  assert.deepEqual(eligible.required, ['eligible', 'missedLocalDate', 'policyVersion', 'streak']);
  assert.deepEqual(ineligible.required, ['eligible', 'reasonCode', 'policyVersion', 'streak']);
  assert.equal(eligible.additionalProperties, false); assert.equal(ineligible.additionalProperties, false);
  const claim = contract.components.schemas.StreakRecoveryClaimResult;
  assert.deepEqual(claim.required, ['status', 'missedLocalDate', 'policyVersion', 'acceptedAt']);
  for (const forbidden of ['timezone', 'xp', 'reward', 'qualifyingAction', 'sourceEvent', 'mission']) assert.equal(Object.hasOwn(claim.properties, forbidden), false);
  assert.ok(contract.components.schemas.ErrorCode.enum.includes('streak_recovery.ineligible'));
});
