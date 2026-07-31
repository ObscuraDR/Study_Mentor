import { createHash } from 'node:crypto';
import { v7 as uuidv7 } from 'uuid';
import { ApiError } from '../errors.js';

function roundedPercent(completed, total) {
  return total === 0 ? 0 : Math.round((completed / total) * 10_000) / 100;
}

// Highest award seen for each lesson, summed. Repeating a lesson never adds XP.
function totalXpPerDistinctLesson(events) {
  const highestPerLesson = new Map();
  for (const event of events) {
    const current = highestPerLesson.get(event.lessonId) ?? 0;
    if (event.xpEarned > current) highestPerLesson.set(event.lessonId, event.xpEarned);
  }
  return [...highestPerLesson.values()].reduce((total, xp) => total + xp, 0);
}

export function calculateProgressProjection({ catalog, events }) {
  const subjects = new Map();
  const topics = new Map();
  const lessons = new Map();
  for (const row of catalog) {
    if (row.subjectId) subjects.set(row.subjectId, { topicIds: new Set() });
    if (row.topicId) {
      topics.set(row.topicId, { subjectId: row.subjectId, lessonIds: new Set() });
      subjects.get(row.subjectId)?.topicIds.add(row.topicId);
    }
    if (row.lessonId) {
      lessons.set(row.lessonId, row.topicId);
      topics.get(row.topicId)?.lessonIds.add(row.lessonId);
    }
  }
  const completedLessonIds = new Set(events.filter((event) => event.eventType === 'lesson.completed').map((event) => event.lessonId));
  const completedTopics = new Set([...topics].filter(([, topic]) => topic.lessonIds.size > 0 && [...topic.lessonIds].every((lessonId) => completedLessonIds.has(lessonId))).map(([topicId]) => topicId));
  const completedSubjects = new Set([...subjects].filter(([, subject]) => subject.topicIds.size > 0 && [...subject.topicIds].every((topicId) => completedTopics.has(topicId))).map(([subjectId]) => subjectId));
  return {
    completedLessons: [...lessons.keys()].filter((lessonId) => completedLessonIds.has(lessonId)).length,
    totalLessons: lessons.size,
    completedTopics: completedTopics.size,
    totalTopics: topics.size,
    completedSubjects: completedSubjects.size,
    totalSubjects: subjects.size,
    // XP is counted once per lesson (Decision 01). Awarding is already capped at
    // write time; counting per distinct lesson here keeps the projection correct
    // for events recorded before that rule existed, and for any concurrent write
    // that slipped past the check.
    totalXp: totalXpPerDistinctLesson(events),
    // Study time is genuinely spent on every visit, so it sums across all events.
    learningTimeSeconds: events.reduce((total, event) => total + event.durationSeconds, 0),
    completionPercentage: roundedPercent([...lessons.keys()].filter((lessonId) => completedLessonIds.has(lessonId)).length, lessons.size),
  };
}

// Earliest accepted lesson.completed event per lesson. A repeat completion of
// the same lesson is a real, separately stored event (see appendLearningEvent)
// but never changes completedAt and never adds a second entry here.
export function deriveLessonCompletions(events) {
  const earliestOccurredAt = new Map();
  for (const event of events) {
    if (event.eventType !== 'lesson.completed') continue;
    const current = earliestOccurredAt.get(event.lessonId);
    if (current === undefined || event.occurredAt < current) earliestOccurredAt.set(event.lessonId, event.occurredAt);
  }
  return [...earliestOccurredAt.entries()]
    .map(([lessonId, completedAt]) => ({ lessonId, completedAt }))
    .sort((left, right) => left.completedAt.localeCompare(right.completedAt));
}

function payloadHash(input) { return createHash('sha256').update(JSON.stringify(input)).digest('hex'); }
// Award policy has one backend owner. Clients submit no award value.
function awardedXp(difficulty) { return { beginner: 10, intermediate: 20, advanced: 30 }[difficulty]; }

export class LearningService {
  constructor(repository, { now = () => new Date(), futureToleranceSeconds = 300 } = {}) {
    this.repository = repository;
    this.now = now;
    this.futureToleranceSeconds = futureToleranceSeconds;
  }

  async listSubjects() { return this.repository.listSubjects(); }
  async getSubject(subjectId) { return this.required(await this.repository.findSubject(subjectId), 'subject'); }
  async listTopics(subjectId) { await this.getSubject(subjectId); return this.repository.listTopics(subjectId); }
  async getTopic(topicId) { return this.required(await this.repository.findTopic(topicId), 'topic'); }
  async listLessons(topicId) { await this.getTopic(topicId); return this.repository.listLessons(topicId); }
  async getLesson(lessonId) { return this.required(await this.repository.findLesson(lessonId), 'lesson'); }

  async appendEvent({ userId, idempotencyKey, input }) {
    const lesson = await this.repository.findLesson(input.lessonId);
    if (!lesson) throw new ApiError(422, 'learning.unknown_lesson', 'The lesson does not exist or is inactive.');
    const clientInput = input;
    const event = { id: uuidv7(), userId, ...clientInput, xpEarned: awardedXp(lesson.difficulty), idempotencyKey };
    // Idempotency compares only normalized client-owned request fields. Derived
    // XP is intentionally excluded, so a retry never reclaims or recalculates it.
    const result = await this.repository.appendLearningEvent({ event, payloadHash: payloadHash(clientInput) });
    if (result.status === 'conflict') throw new ApiError(409, 'learning.idempotency_key_reused', 'The idempotency key was already used with different event data.');
    return { ...result, progress: await this.getProgress(userId) };
  }

  async getProgress(userId) { return calculateProgressProjection(await this.repository.getProgressSource(userId)); }
  async listCompletions(userId) { return deriveLessonCompletions(await this.repository.listCompletionEvents(userId)); }
  required(value, kind) {
    if (!value) throw new ApiError(404, 'learning.resource_not_found', `The requested ${kind} was not found.`);
    return value;
  }
}
