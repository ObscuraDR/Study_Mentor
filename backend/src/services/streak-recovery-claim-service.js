import { createHash } from 'node:crypto';
import { STREAK_RECOVERY_POLICY_VERSION, StreakRecoveryEligibilityService } from './streak-recovery-eligibility-service.js';

function resultFrom(recovery, replayed = false) {
  return {
    status: 'accepted',
    replayed,
    missedLocalDate: recovery.missedLocalDate,
    policyVersion: recovery.policyVersion,
    acceptedAt: recovery.acceptedAt,
  };
}

/** Transactional recovery claim orchestration. It accepts no client-calculated state. */
export class StreakRecoveryClaimService {
  constructor(repository, engagementService, { now = () => new Date(), eligibilityService = null } = {}) {
    this.repository = repository;
    this.engagementService = engagementService;
    this.now = now;
    this.eligibilityService = eligibilityService ?? new StreakRecoveryEligibilityService(repository, { now });
  }

  async claim({ userId, idempotencyKey }) {
    return this.repository.withStreakRecoveryClaimTransaction(userId, async (executor) => {
      const replay = await this.repository.findStreakRecoveryByIdempotency({ userId, idempotencyKey, executor });
      if (replay) return resultFrom(replay, true);

      const evaluation = await this.eligibilityService.getClaimContext(userId, { executor });
      if (!evaluation.projection.eligible) {
        return { status: 'ineligible', reasonCode: evaluation.projection.reasonCode, policyVersion: STREAK_RECOVERY_POLICY_VERSION };
      }

      const recovery = await this.repository.createStreakRecovery({
        userId,
        policyVersion: STREAK_RECOVERY_POLICY_VERSION,
        missedLocalDate: evaluation.projection.missedLocalDate,
        timezone: evaluation.timezone,
        qualifyingActionType: evaluation.qualifyingAction.type,
        qualifyingActionId: evaluation.qualifyingAction.id,
        idempotencyKey,
        payloadHash: createHash('sha256').update(`${STREAK_RECOVERY_POLICY_VERSION}:${userId}:${idempotencyKey}`).digest('hex'),
        acceptedAt: this.now().toISOString(),
        executor,
      });
      if (!recovery) {
        const existing = await this.repository.findStreakRecoveryByIdempotency({ userId, idempotencyKey, executor });
        if (existing) return resultFrom(existing, true);
        const current = await this.eligibilityService.get(userId, { executor });
        return { status: 'ineligible', reasonCode: current.reasonCode ?? 'claim_conflict', policyVersion: STREAK_RECOVERY_POLICY_VERSION };
      }

      await this.engagementService.reconcileStreakAchievements(userId, { executor });
      return resultFrom(recovery);
    });
  }
}
