// P5-01B: Process-local burst and rolling rate admission.
// Honest limitation: correct only for single-process deployments.
// Horizontal scaling requires a shared rate-limit store.

export function createRateAdmission({ burstLimit, burstWindowMs, rollingLimit, rollingWindowMs }) {
  const userBurstCounters = new Map();
  const userRollingTimestamps = new Map();

  function nowMs() { return Date.now(); }

  // Prune expired entries from a user's rolling window
  function pruneRolling(userId, now) {
    const timestamps = userRollingTimestamps.get(userId);
    if (!timestamps) return;
    const cutoff = now - rollingWindowMs;
    while (timestamps.length && timestamps[0] < cutoff) {
      timestamps.shift();
    }
    if (!timestamps.length) userRollingTimestamps.delete(userId);
  }

  return {
    evaluateBurst(userId) {
      const now = nowMs();
      const entry = userBurstCounters.get(userId);
      if (!entry) return 0 < burstLimit;
      if (now - entry.windowStart >= burstWindowMs) {
        userBurstCounters.delete(userId);
        return 0 < burstLimit;
      }
      return entry.count < burstLimit;
    },

    evaluateRolling(userId) {
      const now = nowMs();
      pruneRolling(userId, now);
      const timestamps = userRollingTimestamps.get(userId);
      if (!timestamps) return 0 < rollingLimit;
      return timestamps.length < rollingLimit;
    },

    evaluateAll(userId) {
      return this.evaluateBurst(userId) && this.evaluateRolling(userId);
    },

    consumeAdmission(userId) {
      const now = nowMs();
      // Burst
      let entry = userBurstCounters.get(userId);
      if (!entry || now - entry.windowStart >= burstWindowMs) {
        entry = { count: 0, windowStart: now };
        userBurstCounters.set(userId, entry);
      }
      entry.count += 1;
      // Rolling
      let timestamps = userRollingTimestamps.get(userId);
      if (!timestamps) {
        timestamps = [];
        userRollingTimestamps.set(userId, timestamps);
      }
      pruneRolling(userId, now);
      timestamps.push(now);
    },
  };
}
