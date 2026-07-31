// P5-01B: Fake AI provider for development and testing.
// Returns a deterministic response based on the lesson context.
// Supports refusal for out-of-scope messages.

export function createFakeTutorProvider({ defaultTimeoutMs = 15000 } = {}) {
  return {
    async generate({ lessonId, message, lessonContext, timeoutMs = defaultTimeoutMs }) {
      // Simulate a provider call (actual provider would be an HTTP request)
      // For the fake provider we simply return immediately

      // Check for refusal trigger
      const lowerMessage = message.toLowerCase().trim();
      const refusalTriggers = ['hack', 'violence', 'illegal', 'hack the'];
      const shouldRefuse = refusalTriggers.some((trigger) => lowerMessage.includes(trigger));

      if (shouldRefuse) {
        await delay(20); // Small simulated delay
        return {
          refused: true,
          answer: 'I cannot help with that request because it is outside the scope of English language instruction.',
        };
      }

      await delay(10); // Simulate brief processing

      return {
        answer: `Here is some guidance on "${lessonContext.title}" (${lessonContext.subjectName} > ${lessonContext.topicName}, ${lessonContext.difficulty}): Based on your message "${message}", consider practicing the core vocabulary and sentence patterns from this lesson.`,
        refused: false,
        truncated: false,
      };
    },
  };
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
