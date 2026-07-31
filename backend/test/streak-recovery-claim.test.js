import assert from 'node:assert/strict';
import test from 'node:test';
import { StreakRecoveryClaimService } from '../src/services/streak-recovery-claim-service.js';

const userId = '019f7e39-2000-7000-8000-000000000001';
const idempotencyKey = '019f7e39-2000-7000-8000-000000000002';
const recovery = { missedLocalDate: '2026-03-09', policyVersion: 'streak-recovery-v1', acceptedAt: '2026-03-10T12:00:00.000Z' };
const eligibleContext = { projection: { eligible: true, missedLocalDate: recovery.missedLocalDate, policyVersion: recovery.policyVersion }, qualifyingAction: { type: 'learning_event', id: '019f7e39-2000-7000-8000-000000000003' }, timezone: 'Asia/Ho_Chi_Minh' };

function harness({ existing = null, evaluation = eligibleContext, created = recovery, afterConflict = { eligible: false, reasonCode: 'claim_conflict' }, onCreate = null } = {}) {
  const calls = { create: 0, reconcile: 0 };
  const repository = {
    async withStreakRecoveryClaimTransaction(_userId, callback) { return callback({}); },
    async findStreakRecoveryByIdempotency() { return existing; },
    async createStreakRecovery(input) { calls.create += 1; onCreate?.(input); return created; },
  };
  const eligibilityService = {
    async getClaimContext() { return evaluation; },
    async get() { return afterConflict; },
  };
  const engagementService = { async reconcileStreakAchievements() { calls.reconcile += 1; } };
  return { service: new StreakRecoveryClaimService(repository, engagementService, { now: () => new Date(recovery.acceptedAt), eligibilityService }), calls };
}

test('eligible claim writes one private recovery and reconciles only streak achievements', async () => {
  let input;
  const { service, calls } = harness({ onCreate: (value) => { input = value; } });
  const result = await service.claim({ userId, idempotencyKey });
  assert.deepEqual(result, { status: 'accepted', replayed: false, ...recovery });
  assert.equal(calls.create, 1); assert.equal(calls.reconcile, 1);
  assert.equal(input.userId, userId); assert.equal(input.idempotencyKey, idempotencyKey);
  assert.equal(input.missedLocalDate, recovery.missedLocalDate); assert.equal(input.timezone, 'Asia/Ho_Chi_Minh');
  assert.match(input.payloadHash, /^[0-9a-f]{64}$/u);
  assert.equal('xp' in input || 'reward' in input || 'mission' in input, false);
});

test('all safe ineligible states do not create a recovery or reconcile rewards', async () => {
  for (const reasonCode of ['no_qualifying_evidence', 'no_current_qualifying_action', 'no_single_day_gap', 'gap_exceeds_one_day', 'prior_streak_too_short', 'rolling_limit_reached', 'missed_date_already_recovered', 'adjacent_recovered_date', 'timezone_effective_boundary']) {
    const { service, calls } = harness({ evaluation: { projection: { eligible: false, reasonCode } } });
    assert.deepEqual(await service.claim({ userId, idempotencyKey }), { status: 'ineligible', reasonCode, policyVersion: 'streak-recovery-v1' });
    assert.equal(calls.create, 0); assert.equal(calls.reconcile, 0);
  }
});

test('existing idempotency record returns the original accepted result without evaluation or a second award', async () => {
  const { service, calls } = harness({ existing: recovery, evaluation: { projection: { eligible: false, reasonCode: 'unexpected' } } });
  assert.deepEqual(await service.claim({ userId, idempotencyKey }), { status: 'accepted', replayed: true, ...recovery });
  assert.equal(calls.create, 0); assert.equal(calls.reconcile, 0);
});

test('a conflict-safe failed insert returns a re-evaluated safe reason and no rewards', async () => {
  const { service, calls } = harness({ created: null, afterConflict: { eligible: false, reasonCode: 'adjacent_recovered_date' } });
  assert.deepEqual(await service.claim({ userId, idempotencyKey }), { status: 'ineligible', reasonCode: 'adjacent_recovered_date', policyVersion: 'streak-recovery-v1' });
  assert.equal(calls.create, 1); assert.equal(calls.reconcile, 0);
});
