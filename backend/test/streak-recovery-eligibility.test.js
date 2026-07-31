import assert from 'node:assert/strict';
import test from 'node:test';
import { STREAK_RECOVERY_POLICY_VERSION, StreakRecoveryEligibilityService } from '../src/services/streak-recovery-eligibility-service.js';

const now = '2026-03-10T12:00:00.000Z';

function action(acceptedAt) { return { actionType: 'learning_event', actionId: acceptedAt, acceptedAt }; }
function source({ timezone = 'Asia/Ho_Chi_Minh', timezoneEffectiveAt = '2020-01-01T00:00:00.000Z', actions = [] } = {}) {
  return { timezone, timezoneEffectiveAt, actions };
}
function repository({ evidence = source(), recoveries = [] } = {}) {
  return {
    async getStreakRecoveryEligibilitySource() { return evidence; },
    async listStreakRecoveries() { return recoveries; },
  };
}
function evaluator(options) { return new StreakRecoveryEligibilityService(repository(options), { now: () => new Date(now) }); }
function eligibleEvidence(extra = []) {
  return source({ actions: [
    action('2026-03-06T12:00:00.000Z'), action('2026-03-07T12:00:00.000Z'), action('2026-03-08T12:00:00.000Z'),
    action('2026-03-10T12:00:00.000Z'), ...extra,
  ] });
}

test('eligibility requires exactly a one-day gap following at least three local qualifying days', async () => {
  const eligible = await evaluator({ evidence: eligibleEvidence() }).get('learner');
  assert.deepEqual(eligible, { eligible: true, missedLocalDate: '2026-03-09', policyVersion: STREAK_RECOVERY_POLICY_VERSION });

  const twoDays = await evaluator({ evidence: source({ actions: [
    action('2026-03-07T12:00:00.000Z'), action('2026-03-08T12:00:00.000Z'), action('2026-03-10T12:00:00.000Z'),
  ] }) }).get('learner');
  assert.deepEqual(twoDays, { eligible: false, reasonCode: 'prior_streak_too_short', policyVersion: STREAK_RECOVERY_POLICY_VERSION });

  const zeroGap = await evaluator({ evidence: eligibleEvidence([action('2026-03-09T12:00:00.000Z')]) }).get('learner');
  assert.deepEqual(zeroGap, { eligible: false, reasonCode: 'no_single_day_gap', policyVersion: STREAK_RECOVERY_POLICY_VERSION });

  const multiDayGap = await evaluator({ evidence: source({ actions: [action('2026-03-06T12:00:00.000Z'), action('2026-03-07T12:00:00.000Z'), action('2026-03-10T12:00:00.000Z')] }) }).get('learner');
  assert.deepEqual(multiDayGap, { eligible: false, reasonCode: 'gap_exceeds_one_day', policyVersion: STREAK_RECOVERY_POLICY_VERSION });
});

test('eligibility requires a qualifying action on the current server-derived local date', async () => {
  const result = await evaluator({ evidence: source({ actions: [
    action('2026-03-06T12:00:00.000Z'), action('2026-03-07T12:00:00.000Z'), action('2026-03-08T12:00:00.000Z'),
  ] }) }).get('learner');
  assert.deepEqual(result, { eligible: false, reasonCode: 'no_current_qualifying_action', policyVersion: STREAK_RECOVERY_POLICY_VERSION });
});

test('rolling thirty-day cap is server-time based and permits the exact boundary', async () => {
  const boundary = await evaluator({ evidence: eligibleEvidence(), recoveries: [{ acceptedAt: '2026-02-08T12:00:00.000Z', missedLocalDate: '2026-02-01' }] }).get('learner');
  assert.equal(boundary.eligible, true);
  const insideWindow = await evaluator({ evidence: eligibleEvidence(), recoveries: [{ acceptedAt: '2026-02-08T12:00:00.001Z', missedLocalDate: '2026-02-01' }] }).get('learner');
  assert.deepEqual(insideWindow, { eligible: false, reasonCode: 'rolling_limit_reached', policyVersion: STREAK_RECOVERY_POLICY_VERSION });
});

test('existing and adjacent recovered local dates prevent a second recovery without exposing audit details', async () => {
  const existing = await evaluator({ evidence: eligibleEvidence(), recoveries: [{ acceptedAt: '2026-01-01T00:00:00.000Z', missedLocalDate: '2026-03-09' }] }).get('learner');
  assert.deepEqual(existing, { eligible: false, reasonCode: 'missed_date_already_recovered', policyVersion: STREAK_RECOVERY_POLICY_VERSION });
  const adjacent = await evaluator({ evidence: eligibleEvidence(), recoveries: [{ acceptedAt: '2026-01-01T00:00:00.000Z', missedLocalDate: '2026-03-08' }] }).get('learner');
  assert.deepEqual(adjacent, { eligible: false, reasonCode: 'adjacent_recovered_date', policyVersion: STREAK_RECOVERY_POLICY_VERSION });
});

test('DST local-date boundaries and timezone-effective boundary are safe and deterministic', async () => {
  const dstService = new StreakRecoveryEligibilityService(repository({ evidence: source({
    timezone: 'America/New_York',
    actions: [action('2026-03-06T17:00:00.000Z'), action('2026-03-07T17:00:00.000Z'), action('2026-03-08T16:00:00.000Z'), action('2026-03-10T16:00:00.000Z')],
  }) }), { now: () => new Date('2026-03-10T16:30:00.000Z') });
  assert.deepEqual(await dstService.get('learner'), { eligible: true, missedLocalDate: '2026-03-09', policyVersion: STREAK_RECOVERY_POLICY_VERSION });

  const changedTimezone = await evaluator({ evidence: source({
    timezoneEffectiveAt: '2026-03-09T00:00:00.000Z',
    actions: eligibleEvidence().actions,
  }) }).get('learner');
  assert.deepEqual(changedTimezone, { eligible: false, reasonCode: 'timezone_effective_boundary', policyVersion: STREAK_RECOVERY_POLICY_VERSION });
});

test('eligibility is read-only and never returns rewards or source-event details', async () => {
  const result = await evaluator({ evidence: eligibleEvidence() }).get('learner');
  assert.deepEqual(Object.keys(result).sort(), ['eligible', 'missedLocalDate', 'policyVersion']);
  assert.equal('xp' in result || 'missions' in result || 'reward' in result || 'actions' in result, false);
});
