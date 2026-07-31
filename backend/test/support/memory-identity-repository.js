import { v7 as uuidv7 } from 'uuid';

function copy(value) { return value && { ...value }; }

const seedSubjects = [{ id: '019f7e39-0000-7000-8000-000000000001', slug: 'english-foundations', name: 'English Foundations', displayOrder: 1, active: true }];
const seedTopics = [
  { id: '019f7e39-0001-7000-8000-000000000001', subjectId: seedSubjects[0].id, slug: 'greetings', name: 'Greetings', displayOrder: 1, active: true },
  { id: '019f7e39-0002-7000-8000-000000000001', subjectId: seedSubjects[0].id, slug: 'daily-routines', name: 'Daily routines', displayOrder: 2, active: true },
];
const seedLessons = [
  { id: '019f7e39-0003-7000-8000-000000000001', topicId: seedTopics[0].id, slug: 'hello-and-goodbye', title: 'Hello and goodbye', description: 'Use common greetings and farewells in everyday conversations.', estimatedMinutes: 10, difficulty: 'beginner', displayOrder: 1, active: true },
  { id: '019f7e39-0004-7000-8000-000000000001', topicId: seedTopics[0].id, slug: 'introducing-yourself', title: 'Introducing yourself', description: 'Introduce yourself with a name and a simple personal detail.', estimatedMinutes: 12, difficulty: 'beginner', displayOrder: 2, active: true },
  { id: '019f7e39-0005-7000-8000-000000000001', topicId: seedTopics[1].id, slug: 'morning-routine', title: 'Morning routine', description: 'Describe a simple morning routine using present-tense verbs.', estimatedMinutes: 15, difficulty: 'beginner', displayOrder: 1, active: true },
];
const seedQuizzes = [
  { id: '019f7e39-0006-7000-8000-000000000001', lessonId: seedLessons[0].id, slug: 'hello-and-goodbye-check', title: 'Hello and goodbye check', description: 'Check common greetings and farewells.', displayOrder: 1, active: true },
  { id: '019f7e39-0015-7000-8000-000000000001', lessonId: seedLessons[1].id, slug: 'inactive-introduction-check', title: 'Inactive introduction check', description: 'Inactive development fixture.', displayOrder: 2, active: false },
];
const seedQuestions = [
  { id: '019f7e39-0007-7000-8000-000000000001', quizId: seedQuizzes[0].id, prompt: 'Which greeting is appropriate in the morning?', type: 'single-choice', displayOrder: 1 },
  { id: '019f7e39-0008-7000-8000-000000000001', quizId: seedQuizzes[0].id, prompt: 'Which phrase is a farewell?', type: 'single-choice', displayOrder: 2 },
  { id: '019f7e39-0016-7000-8000-000000000001', quizId: seedQuizzes[1].id, prompt: 'Inactive question?', type: 'single-choice', displayOrder: 1 },
];
const seedOptions = [
  { id: '019f7e39-0009-7000-8000-000000000001', questionId: seedQuestions[0].id, text: 'Good morning', displayOrder: 1, correct: true },
  { id: '019f7e39-0010-7000-8000-000000000001', questionId: seedQuestions[0].id, text: 'Good night', displayOrder: 2, correct: false },
  { id: '019f7e39-0011-7000-8000-000000000001', questionId: seedQuestions[0].id, text: 'See you later', displayOrder: 3, correct: false },
  { id: '019f7e39-0012-7000-8000-000000000001', questionId: seedQuestions[1].id, text: 'Hello', displayOrder: 1, correct: false },
  { id: '019f7e39-0013-7000-8000-000000000001', questionId: seedQuestions[1].id, text: 'Goodbye', displayOrder: 2, correct: true },
  { id: '019f7e39-0014-7000-8000-000000000001', questionId: seedQuestions[1].id, text: 'Good afternoon', displayOrder: 3, correct: false },
  { id: '019f7e39-0017-7000-8000-000000000001', questionId: seedQuestions[2].id, text: 'Inactive answer', displayOrder: 1, correct: true },
];
const seedBossChallenge = {
  id: '019f7e39-0200-7000-8000-000000000001',
  zoneId: seedSubjects[0].id,
  title: 'Reef Guardian',
  description: 'Show what you learned in the English Foundations zone.',
  passingPercentage: 80,
  rewardShells: 25,
  active: true,
};
const seedBossQuestions = [
  { id: '019f7e39-0201-7000-8000-000000000001', challengeId: seedBossChallenge.id, prompt: 'Which greeting is appropriate in the morning?', displayOrder: 1 },
  { id: '019f7e39-0202-7000-8000-000000000001', challengeId: seedBossChallenge.id, prompt: 'Which phrase is a farewell?', displayOrder: 2 },
];
const seedBossOptions = [
  { id: '019f7e39-0203-7000-8000-000000000001', questionId: seedBossQuestions[0].id, text: 'Good morning', displayOrder: 1, correct: true },
  { id: '019f7e39-0204-7000-8000-000000000001', questionId: seedBossQuestions[0].id, text: 'Good night', displayOrder: 2, correct: false },
  { id: '019f7e39-0205-7000-8000-000000000001', questionId: seedBossQuestions[1].id, text: 'Hello', displayOrder: 1, correct: false },
  { id: '019f7e39-0206-7000-8000-000000000001', questionId: seedBossQuestions[1].id, text: 'Goodbye', displayOrder: 2, correct: true },
];
const seedShopItems = [
  { id: '019f7e39-0210-7000-8000-000000000001', name: 'Coral profile frame', description: 'A cosmetic coral frame for your profile.', priceShells: 20, available: true },
  { id: '019f7e39-0211-7000-8000-000000000001', name: 'Deep sea theme', description: 'A cosmetic deep sea color theme.', priceShells: 40, available: true },
];

export class MemoryIdentityRepository {
  constructor() {
    this.usersByEmail = new Map(); this.usersById = new Map(); this.profiles = new Map(); this.settings = new Map(); this.families = new Map(); this.sessions = new Map();
    this.subjects = seedSubjects.map(copy); this.topics = seedTopics.map(copy); this.lessons = seedLessons.map(copy); this.events = []; this.eventsByIdempotency = new Map();
    this.quizzes = seedQuizzes.map(copy); this.questions = seedQuestions.map(copy); this.options = seedOptions.map(copy); this.quizAttempts = []; this.quizAttemptsByIdempotency = new Map();
    this.bossChallenge = copy(seedBossChallenge); this.bossQuestions = seedBossQuestions.map(copy); this.bossOptions = seedBossOptions.map(copy); this.bossAttempts = []; this.bossAttemptsByIdempotency = new Map();
    this.shopItems = seedShopItems.map(copy); this.wallets = new Map(); this.inventory = new Map(); this.shopPurchases = []; this.shopPurchasesByIdempotency = new Map();
    // P5-01B: AI tutor storage
    this.tutorRequests = []; // { id, userId, idempotencyKey, requestFingerprint, lessonId, normalizedResponse, state, processingStartedAt, leaseExpiresAt, claimToken, createdAt, completedAt, expiresAt }
    this.tutorRequestsByKey = new Map(); // `${userId}:${idempotencyKey}` → index in tutorRequests array
  }

  async createUserWithProfile({ user, profile, settings }) {
    if (this.usersByEmail.has(user.email)) return null;
    const stored = copy(user);
    this.usersByEmail.set(stored.email, stored);
    this.usersById.set(stored.id, stored);
    this.profiles.set(stored.id, { id: stored.id, displayName: stored.displayName, email: stored.email, avatarKey: null, educationLevel: null, updatedAt: new Date(stored.createdAt), revision: profile.revision });
    this.settings.set(stored.id, { locale: settings.locale, dailyGoalTargetXp: settings.dailyGoalTargetXp, updatedAt: new Date(stored.createdAt), revision: settings.revision });
    return copy(stored);
  }

  async findUserByEmail(email) { return copy(this.usersByEmail.get(email)); }
  async findUserById(id) { return copy(this.usersById.get(id)); }

  async createSession({ family, session }) {
    this.families.set(family.id, { ...family, revokedAt: null, revokedReason: null });
    this.sessions.set(session.tokenHash, { ...session, familyId: family.id, userId: family.userId, rotatedAt: null, revokedAt: null, revokedReason: null });
  }

  async rotateRefreshToken({ tokenHash, nextSession }) {
    const session = this.sessions.get(tokenHash);
    if (!session) return { status: 'invalid' };
    const family = this.families.get(session.familyId);
    if (family.revokedAt || session.revokedAt) {
      if (session.rotatedAt && !family.revokedAt) { family.revokedAt = new Date(); family.revokedReason = 'refresh_reuse'; }
      return { status: session.rotatedAt ? 'reused' : 'revoked' };
    }
    if (new Date(session.expiresAt) <= new Date() || new Date(family.expiresAt) <= new Date()) { family.revokedAt = new Date(); family.revokedReason = 'expired'; return { status: 'expired' }; }
    session.rotatedAt = new Date(); session.revokedAt = new Date(); session.revokedReason = 'rotated';
    this.sessions.set(nextSession.tokenHash, { ...nextSession, expiresAt: family.expiresAt, familyId: family.id, userId: family.userId, rotatedAt: null, revokedAt: null, revokedReason: null });
    return { status: 'rotated', user: copy(this.usersById.get(family.userId)), familyId: family.id, refreshTokenExpiresAt: new Date(family.expiresAt) };
  }

  async isFamilyActive(userId, familyId) {
    const family = this.families.get(familyId);
    return Boolean(family && family.userId === userId && !family.revokedAt && new Date(family.expiresAt) > new Date());
  }
  async revokeFamily(userId, familyId, reason = 'logout') {
    const family = this.families.get(familyId);
    if (!family || family.userId !== userId || family.revokedAt) return false;
    family.revokedAt = new Date(); family.revokedReason = reason; return true;
  }
  async revokeAllFamilies(userId) { for (const family of this.families.values()) if (family.userId === userId && !family.revokedAt) { family.revokedAt = new Date(); family.revokedReason = 'logout_all'; } }

  async findProfile(userId) { const profile = this.profiles.get(userId); return profile && { ...profile, updatedAt: new Date(profile.updatedAt) }; }
  async updateProfile({ userId, revision, patch, nextRevision }) {
    const profile = this.profiles.get(userId);
    if (!profile || profile.revision !== revision) return { status: 'conflict' };
    const user = this.usersById.get(userId);
    if (patch.displayName !== undefined) { user.displayName = patch.displayName; profile.displayName = patch.displayName; }
    if (patch.avatarKey !== undefined) profile.avatarKey = patch.avatarKey;
    if (patch.educationLevel !== undefined) profile.educationLevel = patch.educationLevel;
    profile.revision = nextRevision; profile.updatedAt = new Date();
    return { status: 'updated', profile: await this.findProfile(userId) };
  }

  async findSettings(userId) { const settings = this.settings.get(userId); return settings && { ...settings, updatedAt: new Date(settings.updatedAt) }; }
  async updateSettings({ userId, revision, settings, nextRevision }) {
    const current = this.settings.get(userId);
    if (!current || current.revision !== revision) return { status: 'conflict' };
    current.locale = settings.locale; current.dailyGoalTargetXp = settings.dailyGoalTargetXp; current.revision = nextRevision; current.updatedAt = new Date();
    return { status: 'updated', settings: await this.findSettings(userId) };
  }

  async listSubjects() { return this.subjects.filter((subject) => subject.active).sort((left, right) => left.displayOrder - right.displayOrder).map(copy); }
  async findSubject(subjectId) { return copy(this.subjects.find((subject) => subject.id === subjectId && subject.active)); }
  async listTopics(subjectId) { return this.topics.filter((topic) => topic.subjectId === subjectId && topic.active).sort((left, right) => left.displayOrder - right.displayOrder).map(copy); }
  async findTopic(topicId) {
    const topic = this.topics.find((candidate) => candidate.id === topicId && candidate.active);
    return topic && (await this.findSubject(topic.subjectId)) ? copy(topic) : null;
  }
  async listLessons(topicId) { return this.lessons.filter((lesson) => lesson.topicId === topicId && lesson.active).sort((left, right) => left.displayOrder - right.displayOrder).map(copy); }
  async findLesson(lessonId) {
    const lesson = this.lessons.find((candidate) => candidate.id === lessonId && candidate.active);
    return lesson && (await this.findTopic(lesson.topicId)) ? copy(lesson) : null;
  }
  async listQuizzesByLesson(lessonId) {
    return this.quizzes
      .filter((quiz) => quiz.lessonId === lessonId && quiz.active)
      .sort((left, right) => left.displayOrder - right.displayOrder)
      .map((quiz) => this.quizSummary(quiz));
  }
  async findQuizDetail(quizId) {
    const quiz = this.quizzes.find((candidate) => candidate.id === quizId && candidate.active);
    if (!quiz || !(await this.findLesson(quiz.lessonId))) return null;
    return { ...this.quizSummary(quiz), questions: this.quizQuestions(quizId, false) };
  }
  async findQuizAttemptSource(quizId) {
    const quiz = this.quizzes.find((candidate) => candidate.id === quizId);
    if (!quiz) return null;
    const lesson = this.lessons.find((candidate) => candidate.id === quiz.lessonId);
    const topic = lesson && this.topics.find((candidate) => candidate.id === lesson.topicId);
    const subject = topic && this.subjects.find((candidate) => candidate.id === topic.subjectId);
    return {
      ...copy(quiz),
      lessonActive: Boolean(lesson?.active),
      topicActive: Boolean(topic?.active),
      subjectActive: Boolean(subject?.active),
      questions: this.quizQuestions(quizId, true),
    };
  }
  quizSummary(quiz) {
    return { id: quiz.id, lessonId: quiz.lessonId, title: quiz.title, description: quiz.description, questionCount: this.questions.filter((question) => question.quizId === quiz.id).length, displayOrder: quiz.displayOrder, active: quiz.active };
  }
  quizQuestions(quizId, includeCorrect) {
    return this.questions
      .filter((question) => question.quizId === quizId)
      .sort((left, right) => left.displayOrder - right.displayOrder)
      .map((question) => ({
        id: question.id,
        prompt: question.prompt,
        type: question.type,
        displayOrder: question.displayOrder,
        options: this.options
          .filter((option) => option.questionId === question.id)
          .sort((left, right) => left.displayOrder - right.displayOrder)
          .map((option) => includeCorrect ? copy(option) : { id: option.id, text: option.text, displayOrder: option.displayOrder }),
      }));
  }
  async appendLearningEvent({ event, payloadHash }) {
    const key = `${event.userId}:${event.idempotencyKey}`;
    const existing = this.eventsByIdempotency.get(key);
    if (existing) return existing.payloadHash === payloadHash ? { status: 'replayed', event: copy(existing.event) } : { status: 'conflict' };
    const stored = { ...event, occurredAt: new Date(event.occurredAt).toISOString(), acceptedAt: new Date().toISOString() };
    this.events.push(stored); this.eventsByIdempotency.set(key, { payloadHash, event: stored });
    return { status: 'created', event: copy(stored) };
  }
  async listCompletionEvents(userId) {
    return this.events
      .filter((event) => event.userId === userId && event.eventType === 'lesson.completed')
      .map(copy)
      .sort((left, right) => left.occurredAt.localeCompare(right.occurredAt));
  }
  async getProgressSource(userId) {
    const catalog = this.subjects.filter((subject) => subject.active).flatMap((subject) => this.topics.filter((topic) => topic.subjectId === subject.id && topic.active).flatMap((topic) => this.lessons.filter((lesson) => lesson.topicId === topic.id && lesson.active).map((lesson) => ({ subjectId: subject.id, topicId: topic.id, lessonId: lesson.id }))));
    return { catalog, events: this.events.filter((event) => event.userId === userId).map(copy) };
  }
  async getCampaignSource(userId) {
    return {
      subjects: this.subjects.map(copy),
      topics: this.topics.map(copy),
      lessons: this.lessons.map(copy),
      events: this.events.filter((event) => event.userId === userId).map(copy),
    };
  }
  async createQuizAttempt({ attempt, answers, payloadHash }) {
    const key = `${attempt.userId}:${attempt.idempotencyKey}`;
    const existing = this.quizAttemptsByIdempotency.get(key);
    if (existing) return existing.payloadHash === payloadHash ? { status: 'replayed', result: copy(existing.result) } : { status: 'conflict' };
    const stored = { ...copy(attempt), answers: answers.map(copy) };
    this.quizAttempts.push(stored);
    this.quizAttemptsByIdempotency.set(key, { payloadHash, result: stored.result });
    return { status: 'created', result: copy(stored.result) };
  }

  async listWrongAnswers({ userId, page, pageSize, lessonId, quizId }) {
    const grouped = new Map();
    const attempts = this.quizAttempts.filter((attempt) => attempt.userId === userId).sort((left, right) => right.submittedAt.localeCompare(left.submittedAt));
    for (const attempt of attempts) {
      const quiz = this.quizzes.find((candidate) => candidate.id === attempt.quizId);
      if (!quiz || (lessonId && quiz.lessonId !== lessonId) || (quizId && quiz.id !== quizId)) continue;
      for (const answer of attempt.answers.filter((candidate) => !candidate.correct)) {
        const question = this.questions.find((candidate) => candidate.id === answer.questionId);
        const selected = this.options.find((candidate) => candidate.id === answer.selectedOptionId);
        const correct = this.options.find((candidate) => candidate.questionId === answer.questionId && candidate.correct);
        if (!question || !selected || !correct) continue;
        const existing = grouped.get(question.id);
        if (existing) existing.wrongCount += 1;
        else grouped.set(question.id, {
          questionId: question.id, quizId: quiz.id, quizTitle: quiz.title, lessonId: quiz.lessonId,
          prompt: question.prompt, selectedOptionId: selected.id, selectedOptionText: selected.text,
          correctOptionId: correct.id, correctOptionText: correct.text,
          lastAnsweredAt: attempt.submittedAt, wrongCount: 1,
        });
      }
    }
    const all = [...grouped.values()].sort((left, right) => right.lastAnsweredAt.localeCompare(left.lastAnsweredAt));
    const offset = (page - 1) * pageSize;
    return { items: all.slice(offset, offset + pageSize), page, pageSize, totalItems: all.length, hasNext: offset + pageSize < all.length };
  }

  bossSource(includeCorrect) {
    const challenge = this.bossChallenge;
    const questions = this.bossQuestions
      .filter((question) => question.challengeId === challenge.id)
      .sort((left, right) => left.displayOrder - right.displayOrder)
      .map((question) => ({
        id: question.id, prompt: question.prompt, displayOrder: question.displayOrder,
        options: this.bossOptions
          .filter((option) => option.questionId === question.id)
          .sort((left, right) => left.displayOrder - right.displayOrder)
          .map((option) => includeCorrect ? copy(option) : { id: option.id, text: option.text, displayOrder: option.displayOrder }),
      }));
    return { ...copy(challenge), questionCount: questions.length, available: true, questions };
  }

  async findActiveBossChallenge() {
    return this.bossChallenge.active ? this.bossSource(false) : null;
  }

  async findBossChallengeSource(challengeId) {
    return this.bossChallenge.active && this.bossChallenge.id === challengeId ? this.bossSource(true) : null;
  }

  async createBossAttempt({ attempt, answers, rewardShells, payloadHash }) {
    const key = `${attempt.userId}:${attempt.idempotencyKey}`;
    const existing = this.bossAttemptsByIdempotency.get(key);
    if (existing) return existing.payloadHash === payloadHash ? { status: 'replayed', result: copy(existing.result) } : { status: 'conflict' };
    const alreadyRewarded = this.bossAttempts.some((candidate) => candidate.userId === attempt.userId && candidate.challengeId === attempt.challengeId && candidate.passed && candidate.rewardShells > 0);
    const awarded = alreadyRewarded ? 0 : rewardShells;
    const balance = (this.wallets.get(attempt.userId) ?? 0) + awarded;
    this.wallets.set(attempt.userId, balance);
    const result = {
      attemptId: attempt.id, challengeId: attempt.challengeId, submittedAt: attempt.submittedAt,
      totalQuestions: attempt.totalQuestions, correctAnswers: attempt.correctAnswers,
      scorePercentage: attempt.scorePercentage, passed: attempt.passed,
      rewardShells: awarded, walletBalance: balance,
    };
    this.bossAttempts.push({ ...attempt, answers: answers.map(copy), rewardShells: awarded, result });
    this.bossAttemptsByIdempotency.set(key, { payloadHash, result });
    return { status: 'created', result: copy(result) };
  }

  async getEconomy(userId) {
    const balance = this.wallets.get(userId) ?? 0;
    const owned = this.inventory.get(userId) ?? new Map();
    return {
      currency: 'shell', balance,
      shopItems: this.shopItems.map((item) => ({ ...copy(item), owned: owned.has(item.id) })),
      inventory: [...owned.entries()].map(([itemId, quantity]) => ({ itemId, quantity, equipped: false })),
    };
  }

  async createShopPurchase({ purchaseId, userId, itemId, idempotencyKey, payloadHash, purchasedAt }) {
    const key = `${userId}:${idempotencyKey}`;
    const existing = this.shopPurchasesByIdempotency.get(key);
    if (existing) return existing.payloadHash === payloadHash ? { status: 'replayed', result: copy(existing.result) } : { status: 'conflict' };
    const item = this.shopItems.find((candidate) => candidate.id === itemId);
    if (!item) return { status: 'not_found' };
    if (!item.available) return { status: 'unavailable' };
    const owned = this.inventory.get(userId) ?? new Map();
    if (owned.has(itemId)) return { status: 'owned' };
    const currentBalance = this.wallets.get(userId) ?? 0;
    if (currentBalance < item.priceShells) return { status: 'insufficient_balance' };
    const balance = currentBalance - item.priceShells;
    this.wallets.set(userId, balance);
    owned.set(itemId, 1);
    this.inventory.set(userId, owned);
    const result = { purchaseId, itemId, priceShells: item.priceShells, balance, inventoryQuantity: 1, purchasedAt };
    this.shopPurchases.push({ purchaseId, userId, itemId, idempotencyKey, payloadHash, result });
    this.shopPurchasesByIdempotency.set(key, { payloadHash, result });
    return { status: 'created', result: copy(result) };
  }

  // ── P5-01B AI Tutor repository methods ──

  async findTutorLessonContext(lessonId) {
    const lesson = this.lessons.find((l) => l.id === lessonId && l.active);
    if (!lesson) return null;
    const topic = this.topics.find((t) => t.id === lesson.topicId && t.active);
    if (!topic) return null;
    const subject = this.subjects.find((s) => s.id === topic.subjectId && s.active);
    if (!subject) return null;
    return {
      lessonId: lesson.id,
      title: lesson.title,
      description: lesson.description,
      difficulty: lesson.difficulty,
      subjectName: subject.name,
      topicName: topic.name,
    };
  }

  async admitTutorProviderCall({ userId, idempotencyKey, requestFingerprint, lessonId, now, concurrencyLimit, leaseDurationSeconds }) {
    const nowDate = new Date(now);
    const leaseExpiry = new Date(nowDate.getTime() + leaseDurationSeconds * 1000);
    const key = `${userId}:${idempotencyKey}`;

    // 1. Opportunistic cleanup
    for (let i = this.tutorRequests.length - 1; i >= 0; i--) {
      const row = this.tutorRequests[i];
      if (row.userId !== userId) continue;
      if (row.state === 'completed' && row.expiresAt < nowDate) {
        this.tutorRequests.splice(i, 1);
        this.tutorRequestsByKey.delete(`${row.userId}:${row.idempotencyKey}`);
        continue;
      }
      const abandonedDeadline = new Date(row.leaseExpiresAt.getTime() + 5 * 60 * 1000);
      if (row.state === 'processing' && abandonedDeadline < nowDate) {
        this.tutorRequests.splice(i, 1);
        this.tutorRequestsByKey.delete(`${row.userId}:${row.idempotencyKey}`);
      }
    }

    // 2. Idempotency inspection
    const existing = this.tutorRequestsByKey.get(key);
    if (existing !== undefined) {
      const row = this.tutorRequests[existing];

      // Fingerprint mismatch
      if (row.requestFingerprint !== requestFingerprint) {
        return { outcome: 'fingerprintMismatch' };
      }

      // Completed → replay
      if (row.state === 'completed') {
        return { outcome: 'completedReplay', result: copy(row.normalizedResponse) };
      }

      // Active processing
      if (row.leaseExpiresAt > nowDate) {
        return { outcome: 'activeProcessing' };
      }

      // Stale processing → candidate for reclaim
      // 3. Count other active claims (exclude this stale row)
      const activeCount = this.tutorRequests.filter(
        (r) => r.userId === userId
          && r.state === 'processing'
          && r.leaseExpiresAt > nowDate
          && r.idempotencyKey !== idempotencyKey,
      ).length;

      if (activeCount >= concurrencyLimit) {
        return { outcome: 'concurrencyRejected' };
      }

      // 4. Reclaim
      row.claimToken = uuidv7();
      row.processingStartedAt = nowDate;
      row.leaseExpiresAt = leaseExpiry;
      return {
        outcome: 'claimReclaimed',
        row: {
          id: row.id,
          idempotencyKey: row.idempotencyKey,
          claimToken: row.claimToken,
          lessonId: row.lessonId,
          createdAt: row.createdAt,
        },
      };
    }

    // No existing row — evaluate concurrency for new claim
    const activeCount = this.tutorRequests.filter(
      (r) => r.userId === userId
        && r.state === 'processing'
        && r.leaseExpiresAt > nowDate,
    ).length;

    if (activeCount >= concurrencyLimit) {
      return { outcome: 'concurrencyRejected' };
    }

    // 5. Insert new claim
    const newRow = {
      id: uuidv7(),
      userId,
      idempotencyKey,
      requestFingerprint,
      lessonId,
      normalizedResponse: null,
      state: 'processing',
      processingStartedAt: nowDate,
      claimToken: uuidv7(),
      createdAt: nowDate,
      completedAt: null,
      expiresAt: new Date(nowDate.getTime() + 24 * 60 * 60 * 1000),
    };
    newRow.leaseExpiresAt = new Date(nowDate.getTime() + leaseDurationSeconds * 1000);
    const idx = this.tutorRequests.length;
    this.tutorRequests.push(newRow);
    this.tutorRequestsByKey.set(key, idx);
    return {
      outcome: 'claimCreated',
      row: {
        id: newRow.id,
        idempotencyKey: newRow.idempotencyKey,
        claimToken: newRow.claimToken,
        lessonId: newRow.lessonId,
        createdAt: newRow.createdAt,
      },
    };
  }

  async completeTutorRequest({ userId, idempotencyKey, claimToken, answer, status, now }) {
    const key = `${userId}:${idempotencyKey}`;
    const idx = this.tutorRequestsByKey.get(key);
    if (idx === undefined) return null;

    const row = this.tutorRequests[idx];
    if (row.claimToken !== claimToken || row.state !== 'processing') return null;

    const nowDate = new Date(now);
    if (row.leaseExpiresAt <= nowDate) return null;

    const normalizedResponse = {
      responseId: row.id,
      lessonId: row.lessonId,
      answer,
      createdAt: nowDate.toISOString(),
      status,
    };

    row.normalizedResponse = normalizedResponse;
    row.state = 'completed';
    row.completedAt = nowDate;

    return { outcome: 'completed', result: copy(normalizedResponse) };
  }

  async releaseTutorRequest({ userId, idempotencyKey, claimToken }) {
    const key = `${userId}:${idempotencyKey}`;
    const idx = this.tutorRequestsByKey.get(key);
    if (idx === undefined) return null;

    const row = this.tutorRequests[idx];
    if (row.claimToken !== claimToken || row.state !== 'processing') return null;

    this.tutorRequests.splice(idx, 1);
    this.tutorRequestsByKey.delete(key);

    // Re-index remaining entries for this user
    for (const [k, v] of this.tutorRequestsByKey) {
      const [uid] = k.split(':');
      if (uid === userId && v > idx) {
        this.tutorRequestsByKey.set(k, v - 1);
      }
    }

    return { outcome: 'released' };
  }

  async deleteExpiredTutorRecords(now) {
    const nowDate = new Date(now);
    for (let i = this.tutorRequests.length - 1; i >= 0; i--) {
      const row = this.tutorRequests[i];
      if (row.state === 'completed' && row.expiresAt < nowDate) {
        this.tutorRequests.splice(i, 1);
        this.tutorRequestsByKey.delete(`${row.userId}:${row.idempotencyKey}`);
        continue;
      }
      const abandonedDeadline = new Date(row.leaseExpiresAt.getTime() + 5 * 60 * 1000);
      if (row.state === 'processing' && abandonedDeadline < nowDate) {
        this.tutorRequests.splice(i, 1);
        this.tutorRequestsByKey.delete(`${row.userId}:${row.idempotencyKey}`);
      }
    }
  }
}
