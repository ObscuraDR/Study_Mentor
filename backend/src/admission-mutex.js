// P5-01B: Per-user admission serialization using a tail-promise queue.
// One active operation per user, FIFO for queued requests,
// exception-safe release, ownership-safe Map cleanup.

const admissionTails = new Map();

export async function withTutorAdmissionLock(userId, operation) {
  const previous = admissionTails.get(userId) ?? Promise.resolve();

  let releaseCurrent;
  const currentGate = new Promise((resolve) => {
    releaseCurrent = resolve;
  });

  const currentTail = previous.then(() => currentGate);
  admissionTails.set(userId, currentTail);

  // Wait for the previous owner to finish
  await previous;

  try {
    return await operation();
  } finally {
    // Signal the next waiter
    releaseCurrent();

    // Only the current tail may delete its own Map entry
    if (admissionTails.get(userId) === currentTail) {
      admissionTails.delete(userId);
    }
  }
}
