import assert from 'node:assert/strict';
import test from 'node:test';
import { calculateProgressProjection, deriveLessonCompletions } from '../src/services/learning-service.js';

const catalog = [
  { subjectId: 'subject-a', topicId: 'topic-a', lessonId: 'lesson-a' },
  { subjectId: 'subject-a', topicId: 'topic-a', lessonId: 'lesson-b' },
  { subjectId: 'subject-a', topicId: 'topic-b', lessonId: 'lesson-c' },
  { subjectId: 'subject-b', topicId: 'topic-c', lessonId: 'lesson-d' },
];

test('progress projection derives lesson, topic, subject, XP, time, and percentage values from immutable events', () => {
  const projection = calculateProgressProjection({ catalog, events: [
    { lessonId: 'lesson-a', eventType: 'lesson.completed', xpEarned: 20, durationSeconds: 120 },
    { lessonId: 'lesson-b', eventType: 'lesson.completed', xpEarned: 30, durationSeconds: 180 },
    { lessonId: 'lesson-a', eventType: 'lesson.completed', xpEarned: 5, durationSeconds: 30 },
  ] });
  assert.deepEqual(projection, {
    completedLessons: 2, totalLessons: 4, completedTopics: 1, totalTopics: 3, completedSubjects: 0, totalSubjects: 2,
    // lesson-a is completed twice (20 then 5) and counts once, at 20, so the
    // total is 20 + 30. Study time still sums every visit: 120 + 180 + 30.
    totalXp: 50, learningTimeSeconds: 330, completionPercentage: 50,
  });
});

test('repeating a lesson never increases total XP, but its study time still counts', () => {
  const once = calculateProgressProjection({ catalog, events: [
    { lessonId: 'lesson-a', eventType: 'lesson.completed', xpEarned: 20, durationSeconds: 120 },
  ] });
  const twice = calculateProgressProjection({ catalog, events: [
    { lessonId: 'lesson-a', eventType: 'lesson.completed', xpEarned: 20, durationSeconds: 120 },
    { lessonId: 'lesson-a', eventType: 'lesson.completed', xpEarned: 20, durationSeconds: 90 },
  ] });

  assert.equal(twice.totalXp, once.totalXp);
  assert.equal(twice.completedLessons, once.completedLessons);
  assert.equal(twice.completionPercentage, once.completionPercentage);
  assert.equal(twice.learningTimeSeconds, 210);
});

test('a lesson repeated with a zero award still counts its original XP once', () => {
  // This is the shape the repository now writes: the first completion carries
  // the award and every repeat carries 0.
  const projection = calculateProgressProjection({ catalog, events: [
    { lessonId: 'lesson-a', eventType: 'lesson.completed', xpEarned: 20, durationSeconds: 120 },
    { lessonId: 'lesson-a', eventType: 'lesson.completed', xpEarned: 0, durationSeconds: 60 },
  ] });

  assert.equal(projection.totalXp, 20);
});

test('distinct lessons each contribute their own XP', () => {
  const projection = calculateProgressProjection({ catalog, events: [
    { lessonId: 'lesson-a', eventType: 'lesson.completed', xpEarned: 10, durationSeconds: 60 },
    { lessonId: 'lesson-b', eventType: 'lesson.completed', xpEarned: 20, durationSeconds: 60 },
    { lessonId: 'lesson-c', eventType: 'lesson.completed', xpEarned: 30, durationSeconds: 60 },
  ] });

  assert.equal(projection.totalXp, 60);
});

test('progress projection does not count inactive or unknown lesson events as completed content but retains their accepted history totals', () => {
  const projection = calculateProgressProjection({ catalog: catalog.slice(0, 1), events: [
    { lessonId: 'removed-lesson', eventType: 'lesson.completed', xpEarned: 100, durationSeconds: 60 },
  ] });
  assert.deepEqual(projection, {
    completedLessons: 0, totalLessons: 1, completedTopics: 0, totalTopics: 1, completedSubjects: 0, totalSubjects: 1,
    totalXp: 100, learningTimeSeconds: 60, completionPercentage: 0,
  });
});

test('empty content has a zero completion percentage without division by zero', () => {
  assert.deepEqual(calculateProgressProjection({ catalog: [], events: [] }), {
    completedLessons: 0, totalLessons: 0, completedTopics: 0, totalTopics: 0, completedSubjects: 0, totalSubjects: 0,
    totalXp: 0, learningTimeSeconds: 0, completionPercentage: 0,
  });
});

test('no events derive no completions', () => {
  assert.deepEqual(deriveLessonCompletions([]), []);
});

test('one accepted event derives one completion at its occurredAt', () => {
  const completions = deriveLessonCompletions([
    { lessonId: 'lesson-a', eventType: 'lesson.completed', occurredAt: '2026-07-20T08:00:00Z' },
  ]);
  assert.deepEqual(completions, [{ lessonId: 'lesson-a', completedAt: '2026-07-20T08:00:00Z' }]);
});

test('multiple lessons each derive their own completion, ordered earliest first', () => {
  const completions = deriveLessonCompletions([
    { lessonId: 'lesson-b', eventType: 'lesson.completed', occurredAt: '2026-07-22T08:00:00Z' },
    { lessonId: 'lesson-a', eventType: 'lesson.completed', occurredAt: '2026-07-20T08:00:00Z' },
  ]);
  assert.deepEqual(completions, [
    { lessonId: 'lesson-a', completedAt: '2026-07-20T08:00:00Z' },
    { lessonId: 'lesson-b', completedAt: '2026-07-22T08:00:00Z' },
  ]);
});

test('a lesson completed more than once keeps only its earliest occurredAt, and no duplicate entry', () => {
  const completions = deriveLessonCompletions([
    { lessonId: 'lesson-a', eventType: 'lesson.completed', occurredAt: '2026-07-22T08:00:00Z' },
    { lessonId: 'lesson-a', eventType: 'lesson.completed', occurredAt: '2026-07-20T08:00:00Z' },
    { lessonId: 'lesson-a', eventType: 'lesson.completed', occurredAt: '2026-07-24T08:00:00Z' },
  ]);
  assert.deepEqual(completions, [{ lessonId: 'lesson-a', completedAt: '2026-07-20T08:00:00Z' }]);
});

test('an event of an unknown or non-qualifying type is not counted as a completion', () => {
  // event_type is currently constrained to 'lesson.completed' at the schema
  // level, but the derivation must not assume that will always hold.
  const completions = deriveLessonCompletions([
    { lessonId: 'lesson-a', eventType: 'quiz.completed', occurredAt: '2026-07-20T08:00:00Z' },
  ]);
  assert.deepEqual(completions, []);
});
