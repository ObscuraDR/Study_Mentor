import assert from 'node:assert/strict';
import test from 'node:test';
import { PostgresIdentityRepository } from '../src/repositories/postgres-identity-repository.js';

function repositoryWith(query) {
  const repository = Object.create(PostgresIdentityRepository.prototype);
  repository.pool = { query };
  return repository;
}

test('streak recovery repository primitives bind private user-scoped lookup and claim inputs', async () => {
  const calls = [];
  const repository = repositoryWith(async (sql, values) => {
    calls.push({ sql, values });
    return { rows: [] };
  });
  const userId = '019f7e39-1000-7000-8000-000000000001';
  const idempotencyKey = '019f7e39-1000-7000-8000-000000000002';
  const actionId = '019f7e39-1000-7000-8000-000000000003';

  await repository.findStreakRecoveryByIdempotency({ userId, idempotencyKey });
  await repository.findStreakRecoveryByMissedLocalDate({ userId, missedLocalDate: '2026-07-20' });
  await repository.listStreakRecoveries({ userId, acceptedAtOrAfter: '2026-06-21T00:00:00.000Z' });
  await repository.createStreakRecovery({
    userId, policyVersion: 'streak-recovery-v1', missedLocalDate: '2026-07-20', timezone: 'Asia/Ho_Chi_Minh',
    qualifyingActionType: 'learning_event', qualifyingActionId: actionId, idempotencyKey, payloadHash: 'a'.repeat(64),
  });

  assert.equal(calls.length, 4);
  assert.match(calls[0].sql, /WHERE user_id = \$1 AND idempotency_key = \$2/u);
  assert.deepEqual(calls[0].values, [userId, idempotencyKey]);
  assert.match(calls[1].sql, /WHERE user_id = \$1 AND missed_local_date = \$2/u);
  assert.deepEqual(calls[1].values, [userId, '2026-07-20']);
  assert.match(calls[2].sql, /accepted_at >= \$2/u);
  assert.deepEqual(calls[2].values, [userId, '2026-06-21T00:00:00.000Z']);
  assert.match(calls[3].sql, /INSERT INTO streak_recoveries/u);
  assert.match(calls[3].sql, /ON CONFLICT DO NOTHING/u);
  assert.deepEqual(calls[3].values.slice(0, 8), [userId, 'streak-recovery-v1', '2026-07-20', 'Asia/Ho_Chi_Minh', 'learning_event', actionId, idempotencyKey, 'a'.repeat(64)]);
});
