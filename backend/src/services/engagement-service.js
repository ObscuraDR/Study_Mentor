export const ENGAGEMENT_RULE_VERSION = 'engagement-v1';
export const LEVEL_CURVE_VERSION = 'level-curve-v1';

export function levelProjection(totalXp) {
  const level = Math.floor(Math.sqrt(totalXp / 100)) + 1;
  const currentLevelXp = 100 * (level - 1) ** 2;
  const nextLevelThreshold = 100 * level ** 2;
  const completionPercentage = Math.max(0, Math.min(100,
    Math.round(((totalXp - currentLevelXp) / (nextLevelThreshold - currentLevelXp)) * 10_000) / 100));
  return { totalXp, level, currentLevelXp, nextLevelThreshold, completionPercentage, levelCurveVersion: LEVEL_CURVE_VERSION };
}

function iso(value) { return new Date(value).toISOString(); }
function goal(progress, target) { return { progress, target, completed: progress >= target }; }

/** A read-only projection over accepted, immutable learning evidence. */
export class EngagementService {
  constructor(repository, { now = () => new Date() } = {}) {
    this.repository = repository;
    this.now = now;
  }

  async reconcile(userId) {
    // Only existing first-time lesson XP is copied into the ledger. Quiz and
    // review evidence participates in engagement, never in XP in v1.
    await this.repository.pool.query(`INSERT INTO engagement_awards (id, user_id, source_type, source_id, rule_version, award_kind, amount)
      SELECT gen_random_uuid(), e.user_id, 'learning_event', e.id, $2, 'xp', e.xp_earned
      FROM learning_events e
      WHERE e.user_id = $1 AND e.event_type = 'lesson.completed' AND e.xp_earned > 0
      ON CONFLICT (user_id, source_type, source_id, rule_version) DO NOTHING`, [userId, ENGAGEMENT_RULE_VERSION]);

    const streak = await this.streak(userId);
    const checks = [
      ['first_lesson', `SELECT count(*) >= 1 AS ok FROM learning_events WHERE user_id = $1 AND event_type = 'lesson.completed'`],
      ['curious_learner', `SELECT count(DISTINCT lesson_id) >= 5 AS ok FROM learning_events WHERE user_id = $1 AND event_type = 'lesson.completed'`],
      ['quiz_explorer', `SELECT count(*) >= 5 AS ok FROM quiz_attempts WHERE user_id = $1`],
      ['review_habit', `SELECT count(*) >= 10 AS ok FROM flashcard_reviews WHERE user_id = $1`],
      ['seven_day_learner', `SELECT $1::int >= 7 AS ok`],
      ['lesson_path_10', `SELECT count(DISTINCT e.lesson_id) >= 10 AS ok FROM learning_events e JOIN lessons l ON l.id = e.lesson_id WHERE e.user_id = $1 AND e.event_type = 'lesson.completed' AND l.active`],
      ['lesson_path_25', `SELECT count(DISTINCT e.lesson_id) >= 25 AS ok FROM learning_events e JOIN lessons l ON l.id = e.lesson_id WHERE e.user_id = $1 AND e.event_type = 'lesson.completed' AND l.active`],
      ['subject_explorer_3', `SELECT count(DISTINCT t.subject_id) >= 3 AS ok FROM learning_events e JOIN lessons l ON l.id = e.lesson_id JOIN topics t ON t.id = l.topic_id JOIN subjects s ON s.id = t.subject_id WHERE e.user_id = $1 AND e.event_type = 'lesson.completed' AND l.active AND t.active AND s.active`],
      ['quiz_first_attempt', `SELECT count(*) >= 1 AS ok FROM quiz_attempts WHERE user_id = $1`],
      ['quiz_confident_5', `SELECT count(*) FILTER (WHERE (result->>'scorePercentage')::numeric >= 80) >= 5 AS ok FROM quiz_attempts WHERE user_id = $1`],
      ['reviewer_25', `SELECT count(*) >= 25 AS ok FROM flashcard_reviews WHERE user_id = $1`],
      ['reviewer_100', `SELECT count(*) >= 100 AS ok FROM flashcard_reviews WHERE user_id = $1`],
      ['streak_3_days', `SELECT $1::int >= 3 AS ok`],
      ['streak_14_days', `SELECT $1::int >= 14 AS ok`],
    ];
    for (const [achievementKey, sql] of checks) {
      const result = await this.repository.pool.query(sql, ['seven_day_learner', 'streak_3_days', 'streak_14_days'].includes(achievementKey) ? [streak] : [userId]);
      if (result.rows[0].ok) {
        await this.repository.pool.query(`INSERT INTO achievement_entitlements (user_id, achievement_key, rule_version)
          VALUES ($1, $2, $3) ON CONFLICT (user_id, achievement_key) DO NOTHING`,
        [userId, achievementKey, ENGAGEMENT_RULE_VERSION]);
      }
    }
  }

  async streak(userId, { executor = null } = {}) {
    const result = await (executor ?? this.repository.pool).query(`WITH zone AS (
      SELECT timezone FROM user_settings WHERE user_id = $1
    ), distinct_days AS (
      SELECT DISTINCT (accepted_at AT TIME ZONE (SELECT timezone FROM zone))::date AS day
      FROM (
        SELECT accepted_at FROM learning_events WHERE user_id = $1 AND event_type = 'lesson.completed'
        UNION ALL SELECT accepted_at FROM quiz_attempts WHERE user_id = $1
        UNION ALL SELECT accepted_at FROM flashcard_reviews WHERE user_id = $1
      ) evidence
      UNION
      SELECT missed_local_date AS day FROM streak_recoveries WHERE user_id = $1
    ), numbered_days AS (
      SELECT day, row_number() OVER (ORDER BY day DESC) AS ordinal FROM distinct_days
    )
    SELECT count(*)::int AS streak
    FROM numbered_days
    WHERE day = (($2::timestamptz AT TIME ZONE (SELECT timezone FROM zone))::date - (ordinal - 1)::int)`,
    [userId, this.now().toISOString()]);
    return result.rows[0].streak;
  }

  async reconcileStreakAchievements(userId, { executor = null } = {}) {
    const queryable = executor ?? this.repository.pool;
    const streak = await this.streak(userId, { executor });
    for (const [achievementKey, threshold] of [['seven_day_learner', 7], ['streak_3_days', 3], ['streak_14_days', 14]]) {
      if (streak >= threshold) {
        await queryable.query(`INSERT INTO achievement_entitlements (user_id, achievement_key, rule_version)
          VALUES ($1, $2, $3) ON CONFLICT (user_id, achievement_key) DO NOTHING`,
        [userId, achievementKey, ENGAGEMENT_RULE_VERSION]);
      }
    }
    return streak;
  }

  async get(userId) {
    await this.reconcile(userId);
    const now = this.now().toISOString();
    const summary = await this.repository.pool.query(`WITH ledger AS (
      SELECT COALESCE(sum(amount), 0)::int AS xp FROM engagement_awards WHERE user_id = $1
    )
    SELECT (SELECT xp FROM ledger) AS xp, s.timezone,
      COALESCE((SELECT json_agg(achievement_key ORDER BY achievement_key)
        FROM achievement_entitlements WHERE user_id = $1), '[]'::json) AS achievements
    FROM user_settings s WHERE s.user_id = $1`, [userId]);
    const row = summary.rows[0];
    const streak = await this.streak(userId);
    const missions = await this.repository.pool.query(`WITH zone AS (
      SELECT timezone FROM user_settings WHERE user_id = $1
    ), bounds AS (
      SELECT timezone,
        date_trunc('day', $2::timestamptz AT TIME ZONE timezone) AS day_start,
        date_trunc('week', $2::timestamptz AT TIME ZONE timezone) AS week_start
      FROM zone
    ), local_actions AS (
      SELECT 'lesson' AS action_type, id::text AS action_id, accepted_at AT TIME ZONE (SELECT timezone FROM bounds) AS local_at
      FROM learning_events WHERE user_id = $1 AND event_type = 'lesson.completed'
      UNION ALL SELECT 'quiz', id::text, accepted_at AT TIME ZONE (SELECT timezone FROM bounds)
      FROM quiz_attempts WHERE user_id = $1
      UNION ALL SELECT 'review', id::text, accepted_at AT TIME ZONE (SELECT timezone FROM bounds)
      FROM flashcard_reviews WHERE user_id = $1
    )
    SELECT
      count(*) FILTER (WHERE action_type = 'lesson' AND local_at >= day_start AND local_at < day_start + interval '1 day')::int AS lessons,
      count(*) FILTER (WHERE action_type = 'quiz' AND local_at >= day_start AND local_at < day_start + interval '1 day')::int AS quizzes,
      count(*) FILTER (WHERE action_type = 'review' AND local_at >= day_start AND local_at < day_start + interval '1 day')::int AS reviews,
      count(DISTINCT action_type || ':' || action_id) FILTER (WHERE local_at >= week_start AND local_at < week_start + interval '7 days')::int AS weekly_actions,
      count(DISTINCT local_at::date) FILTER (WHERE local_at >= week_start AND local_at < week_start + interval '7 days')::int AS weekly_days,
      count(DISTINCT action_id) FILTER (WHERE action_type = 'lesson' AND local_at >= week_start AND local_at < week_start + interval '7 days')::int AS weekly_lessons,
      count(*) FILTER (WHERE action_type = 'quiz' AND local_at >= week_start AND local_at < week_start + interval '7 days')::int AS weekly_quizzes,
      count(*) FILTER (WHERE action_type = 'review' AND local_at >= week_start AND local_at < week_start + interval '7 days')::int AS weekly_reviews,
      count(DISTINCT t.subject_id) FILTER (WHERE action_type = 'lesson' AND local_at >= week_start AND local_at < week_start + interval '7 days')::int AS weekly_subjects,
      (day_start AT TIME ZONE timezone) AS day_starts_at,
      ((day_start + interval '1 day') AT TIME ZONE timezone) AS day_ends_at,
      (week_start AT TIME ZONE timezone) AS week_starts_at,
      ((week_start + interval '7 days') AT TIME ZONE timezone) AS week_ends_at
    FROM bounds LEFT JOIN local_actions ON TRUE LEFT JOIN learning_events le ON le.id::text = local_actions.action_id AND local_actions.action_type = 'lesson' LEFT JOIN lessons l ON l.id = le.lesson_id LEFT JOIN topics t ON t.id = l.topic_id
    GROUP BY timezone, day_start, week_start`, [userId, now]);
    const m = missions.rows[0];
    const lesson = goal(m.lessons, 1);
    const quiz = goal(m.quizzes, 1);
    const reviews = goal(m.reviews, 5);
    const actions = goal(m.weekly_actions, 3);
    const days = goal(m.weekly_days, 2);
    const dailyIndex = Math.floor(Date.parse(iso(m.day_starts_at)) / 86_400_000) % 4;
    const weeklyIndex = Math.floor(Date.parse(iso(m.week_starts_at)) / (7 * 86_400_000)) % 4;
    const dailyTemplates = [
      ['daily_complete_lesson', [lesson], lesson.completed],
      ['daily_quiz_attempt', [quiz], quiz.completed],
      ['daily_review_five', [reviews], reviews.completed],
      ['daily_learning_mix', [lesson, quiz], lesson.completed || quiz.completed],
    ];
    const weeklyLessons = goal(m.weekly_lessons, 3);
    const weeklyQuizzes = goal(m.weekly_quizzes, 2);
    const weeklyReviews = goal(m.weekly_reviews, 10);
    const weeklySubjects = goal(m.weekly_subjects, 2);
    const weeklyTemplates = [
      ['weekly_learning_momentum', [actions, days], actions.completed && days.completed],
      ['weekly_lessons_three', [weeklyLessons], weeklyLessons.completed],
      ['weekly_quiz_and_review', [weeklyQuizzes, weeklyReviews], weeklyQuizzes.completed && weeklyReviews.completed],
      ['weekly_subject_exploration', [weeklySubjects], weeklySubjects.completed],
    ];
    const dailyTemplate = dailyTemplates[dailyIndex];
    const weeklyTemplate = weeklyTemplates[weeklyIndex];
    return {
      ...levelProjection(row.xp),
      timezone: row.timezone,
      streak,
      achievements: row.achievements,
      missions: {
        ruleVersion: ENGAGEMENT_RULE_VERSION,
        daily: { startsAt: iso(m.day_starts_at), endsAt: iso(m.day_ends_at), templateKey: dailyTemplate[0], objectives: dailyTemplate[1], lesson, quiz, reviews, completed: dailyTemplate[2] },
        weekly: { startsAt: iso(m.week_starts_at), endsAt: iso(m.week_ends_at), templateKey: weeklyTemplate[0], objectives: weeklyTemplate[1], actions, days, completed: weeklyTemplate[2] },
      },
    };
  }
}
