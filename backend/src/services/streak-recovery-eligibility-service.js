export const STREAK_RECOVERY_POLICY_VERSION = 'streak-recovery-v1';

function localDate(value, timezone) {
  const values = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
    timeZone: timezone,
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date(value)).filter((part) => part.type !== 'literal').map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

function addLocalDays(date, amount) {
  const value = new Date(`${date}T12:00:00.000Z`);
  value.setUTCDate(value.getUTCDate() + amount);
  return value.toISOString().slice(0, 10);
}

function serverTime(value) { return new Date(value).getTime(); }

/**
 * Read-only private eligibility. The conservative timezone-effective boundary
 * prevents a settings/timezone update from relabelling older evidence into a
 * new recovery opportunity until a new post-update streak exists.
 */
export class StreakRecoveryEligibilityService {
  constructor(repository, { now = () => new Date() } = {}) {
    this.repository = repository;
    this.now = now;
  }

  async get(userId, options = {}) {
    return (await this.evaluate(userId, options)).projection;
  }

  async getClaimContext(userId, options = {}) {
    return this.evaluate(userId, options);
  }

  async evaluate(userId, { executor = null } = {}) {
    const now = this.now();
    const source = await this.repository.getStreakRecoveryEligibilitySource(userId, { executor });
    if (!source) return { projection: this.ineligible('no_qualifying_evidence'), qualifyingAction: null };

    const nowMillis = serverTime(now);
    const effectiveAtMillis = source.timezoneEffectiveAt ? serverTime(source.timezoneEffectiveAt) : Number.NEGATIVE_INFINITY;
    const postTimezoneActions = source.actions.filter((action) => serverTime(action.acceptedAt) >= effectiveAtMillis);
    const today = localDate(now, source.timezone);
    const days = new Set(postTimezoneActions.map((action) => localDate(action.acceptedAt, source.timezone)));

    const todayActions = postTimezoneActions.filter((action) => localDate(action.acceptedAt, source.timezone) === today);
    if (!todayActions.length) return { projection: this.ineligible('no_current_qualifying_action'), qualifyingAction: null };

    const missedLocalDate = addLocalDays(today, -1);
    const preGapStart = addLocalDays(today, -2);
    if (days.has(missedLocalDate)) return { projection: this.ineligible('no_single_day_gap'), qualifyingAction: null };
    if (!days.has(preGapStart)) {
      const anyHistoricalPreGapEvidence = source.actions.some((action) => localDate(action.acceptedAt, source.timezone) === preGapStart);
      return { projection: this.ineligible(anyHistoricalPreGapEvidence ? 'timezone_effective_boundary' : 'gap_exceeds_one_day'), qualifyingAction: null };
    }

    let priorStreak = 0;
    for (let day = preGapStart; days.has(day); day = addLocalDays(day, -1)) priorStreak += 1;
    if (priorStreak < 3) return { projection: this.ineligible('prior_streak_too_short'), qualifyingAction: null };

    const recoveries = await this.repository.listStreakRecoveries({ userId, executor });
    if (recoveries.some((recovery) => nowMillis - serverTime(recovery.acceptedAt) < 30 * 24 * 60 * 60 * 1000)) {
      return { projection: this.ineligible('rolling_limit_reached'), qualifyingAction: null };
    }
    if (recoveries.some((recovery) => recovery.missedLocalDate === missedLocalDate)) return { projection: this.ineligible('missed_date_already_recovered'), qualifyingAction: null };
    if (recoveries.some((recovery) => Math.abs(Math.round((Date.parse(`${recovery.missedLocalDate}T00:00:00.000Z`) - Date.parse(`${missedLocalDate}T00:00:00.000Z`)) / 86_400_000)) === 1)) {
      return { projection: this.ineligible('adjacent_recovered_date'), qualifyingAction: null };
    }

    const qualifyingAction = [...todayActions].sort((left, right) => serverTime(right.acceptedAt) - serverTime(left.acceptedAt) || String(right.actionId).localeCompare(String(left.actionId)))[0];
    return {
      projection: { eligible: true, missedLocalDate, policyVersion: STREAK_RECOVERY_POLICY_VERSION },
      qualifyingAction: { type: qualifyingAction.actionType, id: qualifyingAction.actionId },
      timezone: source.timezone,
    };
  }

  ineligible(reasonCode) {
    return { eligible: false, reasonCode, policyVersion: STREAK_RECOVERY_POLICY_VERSION };
  }
}
