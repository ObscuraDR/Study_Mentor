import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const contract = JSON.parse(await readFile(new URL('../../contracts/openapi/ai-study-mentor.v1.openapi.json', import.meta.url), 'utf8'));

test('OpenAPI exposes only an authenticated read-only engagement projection', () => {
  const operation = contract.paths['/me/engagement'].get;
  assert.deepEqual(operation.security, [{ bearerAuth: [] }]);
  assert.deepEqual(Object.keys(operation.responses).sort(), ['200', '401']);
  assert.equal(contract.components.responses.Engagement.content['application/json'].schema.$ref, '#/components/schemas/EngagementEnvelope');
  const projection = contract.components.schemas.EngagementProjection;
  assert.equal(projection.additionalProperties, false);
  assert.deepEqual(projection.required, ['totalXp', 'level', 'currentLevelXp', 'nextLevelThreshold', 'completionPercentage', 'levelCurveVersion', 'timezone', 'streak', 'achievements', 'missions']);
  assert.equal(projection.properties.levelCurveVersion.const, 'level-curve-v1');
  assert.equal(projection.properties.missions.properties.ruleVersion.const, 'engagement-v1');
  assert.equal(contract.components.schemas.UpdateSettingsRequest.properties.timezone.maxLength, 64);
});
