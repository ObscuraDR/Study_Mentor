import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const contract = JSON.parse(await readFile(new URL('../../contracts/openapi/ai-study-mentor.v1.openapi.json', import.meta.url), 'utf8'));

test('OpenAPI exposes the private read-only campaign projection', () => {
  const operation = contract.paths['/me/campaign'].get;
  assert.deepEqual(operation.security, [{ bearerAuth: [] }]);
  assert.deepEqual(Object.keys(operation.responses).sort(), ['200', '401']);
  assert.equal(contract.components.responses.Campaign.content['application/json'].schema.$ref, '#/components/schemas/CampaignEnvelope');
  const projection = contract.components.schemas.CampaignProjection;
  assert.equal(projection.properties.campaignKey.const, 'core-learning-map');
  assert.equal(projection.properties.campaignVersion.const, 'campaign-v1');
  assert.equal(projection.properties.catalogVersion.const, 'catalog-v1');
  assert.equal(projection.properties.accessPolicy.const, 'open-guided');
  assert.equal(projection.properties.recommendedNodeId.anyOf[1].type, 'null');
  assert.deepEqual(contract.components.schemas.CampaignLessonNode.properties.state.enum, ['not_started', 'recommended', 'completed']);
});
