import assert from 'node:assert/strict';
import test from 'node:test';
import { CampaignService } from '../src/services/campaign-service.js';

const subjectA = { id: 's1', slug: 'english', name: 'English', displayOrder: 1, active: true };
const subjectB = { id: 's2', slug: 'math', name: 'Math', displayOrder: 2, active: true };
const topicA = { id: 't1', subjectId: 's1', slug: 'greetings', name: 'Greetings', displayOrder: 1, active: true };
const topicB = { id: 't2', subjectId: 's2', slug: 'numbers', name: 'Numbers', displayOrder: 1, active: true };
const lessonA = { id: 'l1', topicId: 't1', slug: 'hello', title: 'Hello', displayOrder: 1, active: true };
const lessonB = { id: 'l2', topicId: 't1', slug: 'intro', title: 'Intro', displayOrder: 2, active: true };
const lessonC = { id: 'l3', topicId: 't2', slug: 'count', title: 'Count', displayOrder: 1, active: true };

function repository(events = []) {
  return { getCampaignSource: async () => ({ subjects: [subjectB, subjectA], topics: [topicB, topicA], lessons: [lessonC, lessonB, lessonA], events }) };
}

test('campaign projection orders zones, topics and lessons and recommends the first incomplete node', async () => {
  const result = await new CampaignService(repository([{ eventType: 'lesson.completed', lessonId: 'l1' }])).get('user-1');
  assert.equal(result.campaignKey, 'core-learning-map');
  assert.equal(result.accessPolicy, 'open-guided');
  assert.deepEqual(result.zones.map((zone) => zone.subjectId), ['s1', 's2']);
  assert.deepEqual(result.zones[0].topics[0].lessons.map((lesson) => lesson.lessonId), ['l1', 'l2']);
  assert.equal(result.zones[0].topics[0].lessons[0].state, 'completed');
  assert.equal(result.zones[0].topics[0].lessons[1].state, 'recommended');
  assert.equal(result.recommendedNodeId, 'l2');
  assert.equal(result.completedLessons, 1);
  assert.equal(result.totalLessons, 3);
  assert.equal(result.completionPercentage, 33.33);
});

test('empty learner state is open and has no fabricated completion or recommendation', async () => {
  const result = await new CampaignService(repository()).get('user-2');
  assert.equal(result.recommendedNodeId, 'l1');
  assert.equal(result.completedLessons, 0);
  assert.equal(result.zones.every((zone) => zone.completedLessons === 0), true);
  assert.equal(result.zones[0].topics[0].lessons[0].state, 'recommended');
  assert.equal(result.zones[0].topics[0].lessons[1].state, 'not_started');
});

test('inactive subjects, topics, lessons and duplicate evidence are excluded without changing history', async () => {
  const source = repository([
    { eventType: 'lesson.completed', lessonId: 'l1' },
    { eventType: 'lesson.completed', lessonId: 'l1' },
    { eventType: 'lesson.completed', lessonId: 'inactive' },
  ]);
  const original = source.getCampaignSource;
  source.getCampaignSource = async (userId) => {
    const value = await original(userId);
    value.subjects.push({ id: 's3', slug: 'old', name: 'Old', displayOrder: 3, active: false });
    value.lessons.push({ id: 'inactive', topicId: 't1', slug: 'old', title: 'Old', displayOrder: 3, active: false });
    return value;
  };
  const result = await new CampaignService(source).get('user-3');
  assert.equal(result.zones.length, 2);
  assert.equal(result.totalLessons, 3);
  assert.equal(result.completedLessons, 1);
  assert.equal(result.zones.flatMap((zone) => zone.topics.flatMap((topic) => topic.lessons)).some((lesson) => lesson.lessonId === 'inactive'), false);
});
