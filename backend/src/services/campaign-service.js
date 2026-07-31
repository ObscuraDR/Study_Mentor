export const CAMPAIGN_KEY = 'core-learning-map';
export const CAMPAIGN_VERSION = 'campaign-v1';
export const CATALOG_VERSION = 'catalog-v1';

function order(left, right) {
  return left.displayOrder - right.displayOrder || String(left.id).localeCompare(String(right.id));
}

function progress(completedLessons, totalLessons) {
  return {
    completedLessons,
    totalLessons,
    completionPercentage: totalLessons === 0 ? 0 : Math.round((completedLessons / totalLessons) * 10_000) / 100,
  };
}

/** Private, deterministic read projection over active catalog and accepted lesson evidence. */
export class CampaignService {
  constructor(repository) {
    this.repository = repository;
  }

  async get(userId) {
    const source = await this.repository.getCampaignSource(userId);
    const completed = new Set(source.events
      .filter((event) => event.eventType === 'lesson.completed')
      .map((event) => event.lessonId));
    const subjects = source.subjects.filter((subject) => subject.active).sort(order);
    const topics = source.topics.filter((topic) => topic.active).sort(order);
    const lessons = source.lessons.filter((lesson) => lesson.active).sort(order);
    const orderedLessons = [];
    const zones = subjects.map((subject) => {
      const subjectTopics = topics.filter((topic) => topic.subjectId === subject.id).map((topic) => {
        const topicLessons = lessons.filter((lesson) => lesson.topicId === topic.id).sort(order);
        const topicCompleted = topicLessons.filter((lesson) => completed.has(lesson.id)).length;
        topicLessons.forEach((lesson) => orderedLessons.push(lesson));
        return {
          topicId: topic.id,
          slug: topic.slug,
          name: topic.name,
          displayOrder: topic.displayOrder,
          ...progress(topicCompleted, topicLessons.length),
          lessons: topicLessons.map((lesson) => ({
            lessonId: lesson.id,
            slug: lesson.slug,
            title: lesson.title,
            displayOrder: lesson.displayOrder,
            completed: completed.has(lesson.id),
            state: completed.has(lesson.id) ? 'completed' : 'not_started',
          })),
        };
      });
      const subjectCompleted = subjectTopics.reduce((sum, topic) => sum + topic.completedLessons, 0);
      const subjectTotal = subjectTopics.reduce((sum, topic) => sum + topic.totalLessons, 0);
      return {
        subjectId: subject.id,
        slug: subject.slug,
        name: subject.name,
        displayOrder: subject.displayOrder,
        state: 'active',
        ...progress(subjectCompleted, subjectTotal),
        topics: subjectTopics,
      };
    });
    const recommended = orderedLessons.find((lesson) => !completed.has(lesson.id));
    if (recommended) {
      for (const zone of zones) for (const topic of zone.topics) for (const lesson of topic.lessons) {
        if (lesson.lessonId === recommended.id) lesson.state = 'recommended';
      }
    }
    const totalLessons = zones.reduce((sum, zone) => sum + zone.totalLessons, 0);
    const completedLessons = zones.reduce((sum, zone) => sum + zone.completedLessons, 0);
    return {
      campaignKey: CAMPAIGN_KEY,
      campaignVersion: CAMPAIGN_VERSION,
      catalogVersion: CATALOG_VERSION,
      accessPolicy: 'open-guided',
      recommendedNodeId: recommended?.id ?? null,
      ...progress(completedLessons, totalLessons),
      zones,
    };
  }
}
