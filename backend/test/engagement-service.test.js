import assert from 'node:assert/strict';
import test from 'node:test';
import { LEVEL_CURVE_VERSION, levelProjection } from '../src/services/engagement-service.js';

test('level curve v1 is deterministic and preserves the level-one zero-XP boundary', () => {
  assert.deepEqual(levelProjection(0), { totalXp: 0, level: 1, currentLevelXp: 0, nextLevelThreshold: 100, completionPercentage: 0, levelCurveVersion: LEVEL_CURVE_VERSION });
  assert.equal(levelProjection(99).level, 1);
  assert.equal(levelProjection(100).level, 2);
  assert.deepEqual(levelProjection(250), { totalXp: 250, level: 2, currentLevelXp: 100, nextLevelThreshold: 400, completionPercentage: 50, levelCurveVersion: LEVEL_CURVE_VERSION });
});
