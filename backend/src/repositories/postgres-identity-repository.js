import { Pool } from 'pg';

function userFrom(row) {
  return row ? { id: row.id, displayName: row.displayName, email: row.email, passwordHash: row.passwordHash, role: row.role, createdAt: row.createdAt } : null;
}

function profileFrom(row) {
  return row ? { id: row.id, displayName: row.displayName, email: row.email, avatarKey: row.avatarKey, educationLevel: row.educationLevel, updatedAt: row.updatedAt, revision: row.revision } : null;
}

function settingsFrom(row) {
  return row ? { locale: row.locale, dailyGoalTargetXp: row.dailyGoalTargetXp, timezone: row.timezone, updatedAt: row.updatedAt, revision: row.revision } : null;
}

function subjectFrom(row) { return row ? { id: row.id, slug: row.slug, name: row.name, displayOrder: row.displayOrder, active: row.active } : null; }
function topicFrom(row) { return row ? { id: row.id, subjectId: row.subjectId, slug: row.slug, name: row.name, displayOrder: row.displayOrder, active: row.active } : null; }
function lessonFrom(row) { return row ? { id: row.id, topicId: row.topicId, slug: row.slug, title: row.title, description: row.description, estimatedMinutes: row.estimatedMinutes, difficulty: row.difficulty, displayOrder: row.displayOrder, active: row.active } : null; }
function quizSummaryFrom(row) { return row ? { id: row.id, lessonId: row.lessonId, title: row.title, description: row.description, questionCount: row.questionCount, displayOrder: row.displayOrder, active: row.active } : null; }
function eventFrom(row) {
  return row ? {
    id: row.id, userId: row.userId, lessonId: row.lessonId, occurredAt: new Date(row.occurredAt).toISOString(),
    xpEarned: row.xpEarned, durationSeconds: row.durationSeconds, eventType: row.eventType, acceptedAt: new Date(row.acceptedAt).toISOString(),
  } : null;
}

const userSelect = `SELECT id, display_name AS "displayName", email, password_hash AS "passwordHash", role, created_at AS "createdAt" FROM users`;
const profileSelect = `SELECT u.id, u.display_name AS "displayName", u.email, p.avatar_key AS "avatarKey", p.education_level AS "educationLevel", p.updated_at AS "updatedAt", p.revision FROM users u JOIN user_profiles p ON p.user_id = u.id`;
const settingsSelect = `SELECT locale, daily_goal_target_xp AS "dailyGoalTargetXp", timezone, updated_at AS "updatedAt", revision FROM user_settings`;
function toIso(value) { return value === null || value === undefined ? null : new Date(value).toISOString(); }

function flashcardStateFrom(row) {
  return row ? {
    cardId: row.cardId, box: row.box, dueAt: toIso(row.dueAt), lastReviewedAt: toIso(row.lastReviewedAt),
    totalReviews: row.totalReviews, knownReviews: row.knownReviews, algorithmVersion: row.algorithmVersion,
  } : null;
}

function streakRecoveryFrom(row) {
  return row ? {
    id: row.id,
    userId: row.userId,
    policyVersion: row.policyVersion,
    missedLocalDate: row.missedLocalDate,
    timezone: row.timezone,
    qualifyingActionType: row.qualifyingActionType,
    qualifyingActionId: row.qualifyingActionId,
    idempotencyKey: row.idempotencyKey,
    payloadHash: row.payloadHash,
    acceptedAt: toIso(row.acceptedAt),
  } : null;
}

const flashcardStateSelect = `SELECT card_id AS "cardId", box, due_at AS "dueAt", last_reviewed_at AS "lastReviewedAt", total_reviews AS "totalReviews", known_reviews AS "knownReviews", algorithm_version AS "algorithmVersion" FROM flashcard_review_states`;

const subjectSelect = 'SELECT id, slug, name, display_order AS "displayOrder", active FROM subjects';
const topicSelect = 'SELECT id, subject_id AS "subjectId", slug, name, display_order AS "displayOrder", active FROM topics';
const lessonSelect = 'SELECT id, topic_id AS "topicId", slug, title, description, estimated_minutes AS "estimatedMinutes", difficulty, display_order AS "displayOrder", active FROM lessons';
const eventSelect = 'SELECT id, user_id AS "userId", lesson_id AS "lessonId", occurred_at AS "occurredAt", xp_earned AS "xpEarned", duration_seconds AS "durationSeconds", event_type AS "eventType", accepted_at AS "acceptedAt" FROM learning_events';
const quizSummarySelect = 'SELECT q.id, q.lesson_id AS "lessonId", q.title, q.description, COUNT(qq.id)::int AS "questionCount", q.display_order AS "displayOrder", q.active FROM quizzes q LEFT JOIN quiz_questions qq ON qq.quiz_id = q.id';

export class PostgresIdentityRepository {
  constructor(databaseUrl) { this.pool = new Pool({ connectionString: databaseUrl }); }
  async verifyConnection() { await this.pool.query('SELECT 1'); }
  async close() { await this.pool.end(); }

  async createUserWithProfile({ user, profile, settings }) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      await client.query('INSERT INTO users (id, email, display_name, password_hash, role) VALUES ($1, $2, $3, $4, $5)', [user.id, user.email, user.displayName, user.passwordHash, user.role]);
      await client.query('INSERT INTO user_profiles (user_id, revision) VALUES ($1, $2)', [user.id, profile.revision]);
      await client.query('INSERT INTO user_settings (user_id, locale, daily_goal_target_xp, revision) VALUES ($1, $2, $3, $4)', [user.id, settings.locale, settings.dailyGoalTargetXp, settings.revision]);
      await client.query('COMMIT');
      return user;
    } catch (error) {
      await client.query('ROLLBACK');
      if (error.code === '23505') return null;
      throw error;
    } finally { client.release(); }
  }

  async findUserByEmail(email) {
    const result = await this.pool.query(`${userSelect} WHERE email = $1`, [email]);
    return userFrom(result.rows[0]);
  }

  async findUserById(id) {
    const result = await this.pool.query(`${userSelect} WHERE id = $1`, [id]);
    return userFrom(result.rows[0]);
  }

  async createSession({ family, session }) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      await client.query('INSERT INTO refresh_token_families (id, user_id, client_platform, expires_at) VALUES ($1, $2, $3, $4)', [family.id, family.userId, family.clientPlatform, family.expiresAt]);
      await client.query('INSERT INTO sessions (id, family_id, user_id, refresh_token_hash, expires_at) VALUES ($1, $2, $3, $4, $5)', [session.id, family.id, family.userId, session.tokenHash, session.expiresAt]);
      await client.query('COMMIT');
    } catch (error) { await client.query('ROLLBACK'); throw error; } finally { client.release(); }
  }

  async rotateRefreshToken({ tokenHash, nextSession }) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const found = await client.query(`SELECT u.id, u.display_name AS "displayName", u.email, u.password_hash AS "passwordHash", u.role, u.created_at AS "createdAt",
          s.family_id AS "familyId", s.expires_at AS "sessionExpiresAt", s.rotated_at AS "rotatedAt", s.revoked_at AS "sessionRevokedAt",
          f.expires_at AS "familyExpiresAt", f.revoked_at AS "familyRevokedAt"
        FROM sessions s
        JOIN refresh_token_families f ON f.id = s.family_id
        JOIN users u ON u.id = s.user_id
        WHERE s.refresh_token_hash = $1
        FOR UPDATE`, [tokenHash]);
      const row = found.rows[0];
      if (!row) { await client.query('COMMIT'); return { status: 'invalid' }; }
      if (row.familyRevokedAt || row.sessionRevokedAt) {
        if (row.rotatedAt && !row.familyRevokedAt) await client.query("UPDATE refresh_token_families SET revoked_at = NOW(), revoked_reason = 'refresh_reuse' WHERE id = $1 AND revoked_at IS NULL", [row.familyId]);
        await client.query('COMMIT');
        return { status: row.rotatedAt ? 'reused' : 'revoked' };
      }
      if (new Date(row.sessionExpiresAt) <= new Date() || new Date(row.familyExpiresAt) <= new Date()) {
        await client.query("UPDATE refresh_token_families SET revoked_at = NOW(), revoked_reason = 'expired' WHERE id = $1 AND revoked_at IS NULL", [row.familyId]);
        await client.query('COMMIT');
        return { status: 'expired' };
      }
      await client.query("UPDATE sessions SET rotated_at = NOW(), revoked_at = NOW(), revoked_reason = 'rotated' WHERE refresh_token_hash = $1", [tokenHash]);
      await client.query('INSERT INTO sessions (id, family_id, user_id, refresh_token_hash, expires_at) VALUES ($1, $2, $3, $4, $5)', [nextSession.id, row.familyId, row.id, nextSession.tokenHash, row.familyExpiresAt]);
      await client.query('COMMIT');
      return { status: 'rotated', user: userFrom(row), familyId: row.familyId, refreshTokenExpiresAt: new Date(row.familyExpiresAt) };
    } catch (error) { await client.query('ROLLBACK'); throw error; } finally { client.release(); }
  }

  async isFamilyActive(userId, familyId) {
    const result = await this.pool.query('SELECT 1 FROM refresh_token_families WHERE id = $1 AND user_id = $2 AND revoked_at IS NULL AND expires_at > NOW()', [familyId, userId]);
    return result.rowCount === 1;
  }

  async revokeFamily(userId, familyId, reason = 'logout') {
    const result = await this.pool.query('UPDATE refresh_token_families SET revoked_at = NOW(), revoked_reason = $3 WHERE id = $1 AND user_id = $2 AND revoked_at IS NULL', [familyId, userId, reason]);
    return result.rowCount === 1;
  }

  async revokeAllFamilies(userId) {
    await this.pool.query("UPDATE refresh_token_families SET revoked_at = NOW(), revoked_reason = 'logout_all' WHERE user_id = $1 AND revoked_at IS NULL", [userId]);
  }

  async findProfile(userId) {
    const result = await this.pool.query(`${profileSelect} WHERE u.id = $1`, [userId]);
    return profileFrom(result.rows[0]);
  }

  async updateProfile({ userId, revision, patch, nextRevision }) {
    const profileFields = [];
    const values = [];
    let index = 1;
    if (patch.avatarKey !== undefined) { profileFields.push(`avatar_key = $${index++}`); values.push(patch.avatarKey); }
    if (patch.educationLevel !== undefined) { profileFields.push(`education_level = $${index++}`); values.push(patch.educationLevel); }
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      if (patch.displayName !== undefined) await client.query('UPDATE users SET display_name = $1, updated_at = NOW() WHERE id = $2', [patch.displayName, userId]);
      values.push(nextRevision, userId, revision);
      const updated = await client.query(`UPDATE user_profiles SET ${profileFields.join(', ')}${profileFields.length ? ', ' : ''}revision = $${index++}, updated_at = NOW() WHERE user_id = $${index++} AND revision = $${index} RETURNING user_id`, values);
      if (!updated.rowCount) { await client.query('ROLLBACK'); return { status: 'conflict' }; }
      const result = await client.query(`${profileSelect} WHERE u.id = $1`, [userId]);
      await client.query('COMMIT');
      return { status: 'updated', profile: profileFrom(result.rows[0]) };
    } catch (error) { await client.query('ROLLBACK'); throw error; } finally { client.release(); }
  }

  async findSettings(userId) {
    const result = await this.pool.query(`${settingsSelect} WHERE user_id = $1`, [userId]);
    return settingsFrom(result.rows[0]);
  }

  async updateSettings({ userId, revision, settings, nextRevision }) {
    const result = await this.pool.query(
      'UPDATE user_settings SET locale = $1, daily_goal_target_xp = $2, timezone = COALESCE($3, timezone), revision = $4, updated_at = NOW() WHERE user_id = $5 AND revision = $6 RETURNING locale, daily_goal_target_xp AS "dailyGoalTargetXp", timezone, updated_at AS "updatedAt", revision',
      [settings.locale, settings.dailyGoalTargetXp, settings.timezone, nextRevision, userId, revision],
    );
    if (!result.rowCount) return { status: 'conflict' };
    return { status: 'updated', settings: settingsFrom(result.rows[0]) };
  }

  async listSubjects() {
    const result = await this.pool.query(`${subjectSelect} WHERE active = TRUE ORDER BY display_order, id`);
    return result.rows.map(subjectFrom);
  }

  async findSubject(subjectId) {
    const result = await this.pool.query(`${subjectSelect} WHERE id = $1 AND active = TRUE`, [subjectId]);
    return subjectFrom(result.rows[0]);
  }

  async listTopics(subjectId) {
    const result = await this.pool.query(`${topicSelect} WHERE subject_id = $1 AND active = TRUE ORDER BY display_order, id`, [subjectId]);
    return result.rows.map(topicFrom);
  }

  async findTopic(topicId) {
    const result = await this.pool.query('SELECT t.id, t.subject_id AS "subjectId", t.slug, t.name, t.display_order AS "displayOrder", t.active FROM topics t JOIN subjects s ON s.id = t.subject_id WHERE t.id = $1 AND t.active = TRUE AND s.active = TRUE', [topicId]);
    return topicFrom(result.rows[0]);
  }

  async listLessons(topicId) {
    const result = await this.pool.query('SELECT l.id, l.topic_id AS "topicId", l.slug, l.title, l.description, l.estimated_minutes AS "estimatedMinutes", l.difficulty, l.display_order AS "displayOrder", l.active FROM lessons l JOIN topics t ON t.id = l.topic_id JOIN subjects s ON s.id = t.subject_id WHERE l.topic_id = $1 AND l.active = TRUE AND t.active = TRUE AND s.active = TRUE ORDER BY l.display_order, l.id', [topicId]);
    return result.rows.map(lessonFrom);
  }

  async findLesson(lessonId) {
    const result = await this.pool.query('SELECT l.id, l.topic_id AS "topicId", l.slug, l.title, l.description, l.estimated_minutes AS "estimatedMinutes", l.difficulty, l.display_order AS "displayOrder", l.active FROM lessons l JOIN topics t ON t.id = l.topic_id JOIN subjects s ON s.id = t.subject_id WHERE l.id = $1 AND l.active = TRUE AND t.active = TRUE AND s.active = TRUE', [lessonId]);
    return lessonFrom(result.rows[0]);
  }

  async listQuizzesByLesson(lessonId) {
    const result = await this.pool.query(`${quizSummarySelect} JOIN lessons l ON l.id = q.lesson_id JOIN topics t ON t.id = l.topic_id JOIN subjects s ON s.id = t.subject_id WHERE q.lesson_id = $1 AND q.active = TRUE AND l.active = TRUE AND t.active = TRUE AND s.active = TRUE GROUP BY q.id ORDER BY q.display_order, q.id`, [lessonId]);
    return result.rows.map(quizSummaryFrom);
  }

  async findQuizDetail(quizId) {
    const result = await this.pool.query(`${quizSummarySelect} JOIN lessons l ON l.id = q.lesson_id JOIN topics t ON t.id = l.topic_id JOIN subjects s ON s.id = t.subject_id WHERE q.id = $1 AND q.active = TRUE AND l.active = TRUE AND t.active = TRUE AND s.active = TRUE GROUP BY q.id`, [quizId]);
    const quiz = quizSummaryFrom(result.rows[0]);
    if (!quiz) return null;
    quiz.questions = await this.quizQuestions(quizId, false);
    return quiz;
  }

  async findQuizAttemptSource(quizId) {
    const result = await this.pool.query(`SELECT q.id, q.lesson_id AS "lessonId", q.title, q.description, q.display_order AS "displayOrder", q.active,
        l.active AS "lessonActive", t.active AS "topicActive", s.active AS "subjectActive"
      FROM quizzes q
      JOIN lessons l ON l.id = q.lesson_id
      JOIN topics t ON t.id = l.topic_id
      JOIN subjects s ON s.id = t.subject_id
      WHERE q.id = $1`, [quizId]);
    const row = result.rows[0];
    if (!row) return null;
    return {
      id: row.id,
      lessonId: row.lessonId,
      title: row.title,
      description: row.description,
      displayOrder: row.displayOrder,
      active: row.active,
      lessonActive: row.lessonActive,
      topicActive: row.topicActive,
      subjectActive: row.subjectActive,
      questions: await this.quizQuestions(quizId, true),
    };
  }

  async quizQuestions(quizId, includeCorrect) {
    const [questions, options] = await Promise.all([
      this.pool.query('SELECT id, quiz_id AS "quizId", prompt, question_type AS "type", display_order AS "displayOrder" FROM quiz_questions WHERE quiz_id = $1 ORDER BY display_order, id', [quizId]),
      this.pool.query('SELECT o.id, o.question_id AS "questionId", o.text, o.display_order AS "displayOrder", o.correct FROM quiz_answer_options o JOIN quiz_questions q ON q.id = o.question_id WHERE q.quiz_id = $1 ORDER BY q.display_order, o.display_order, o.id', [quizId]),
    ]);
    return questions.rows.map((question) => ({
      id: question.id,
      prompt: question.prompt,
      type: question.type,
      displayOrder: question.displayOrder,
      options: options.rows
        .filter((option) => option.questionId === question.id)
        .map((option) => includeCorrect
          ? { id: option.id, text: option.text, displayOrder: option.displayOrder, correct: option.correct }
          : { id: option.id, text: option.text, displayOrder: option.displayOrder }),
    }));
  }

  async appendLearningEvent({ event, payloadHash }) {
    // A lesson awards XP at most once per user (Decision 01). A repeat
    // completion is still recorded as an event — it is a real thing the learner
    // did — but it earns 0, so XP measures progress rather than activity.
    // The check runs inside the INSERT so it sees the same snapshot as the write.
    const inserted = await this.pool.query(
      `INSERT INTO learning_events (id, user_id, lesson_id, occurred_at, xp_earned, duration_seconds, event_type, idempotency_key, payload_hash)
       SELECT $1, $2, $3, $4,
              CASE WHEN EXISTS (
                SELECT 1 FROM learning_events
                 WHERE user_id = $2 AND lesson_id = $3 AND event_type = $7
              ) THEN 0 ELSE $5::integer END,
              $6, $7, $8, $9
       ON CONFLICT (user_id, idempotency_key) DO NOTHING
       RETURNING id, user_id AS "userId", lesson_id AS "lessonId", occurred_at AS "occurredAt", xp_earned AS "xpEarned", duration_seconds AS "durationSeconds", event_type AS "eventType", accepted_at AS "acceptedAt"`,
      [event.id, event.userId, event.lessonId, event.occurredAt, event.xpEarned, event.durationSeconds, event.eventType, event.idempotencyKey, payloadHash],
    );
    if (inserted.rowCount) return { status: 'created', event: eventFrom(inserted.rows[0]) };
    const existing = await this.pool.query('SELECT id, user_id AS "userId", lesson_id AS "lessonId", occurred_at AS "occurredAt", xp_earned AS "xpEarned", duration_seconds AS "durationSeconds", event_type AS "eventType", accepted_at AS "acceptedAt", payload_hash AS "payloadHash" FROM learning_events WHERE user_id = $1 AND idempotency_key = $2', [event.userId, event.idempotencyKey]);
    if (!existing.rowCount || existing.rows[0].payloadHash !== payloadHash) return { status: 'conflict' };
    return { status: 'replayed', event: eventFrom(existing.rows[0]) };
  }

  // Flashcards ---------------------------------------------------------------
  //
  // Every bind parameter that participates in interval arithmetic or a
  // timestamp comparison is cast explicitly. Without the cast PostgreSQL infers
  // the parameter's type from the surrounding expression and can settle on
  // `interval`, which fails at runtime (see BACKEND-BUG-01).

  async listFlashcardDecks(lessonId) {
    const result = await this.pool.query(
      `SELECT d.id, d.lesson_id AS "lessonId", d.slug, d.name, d.description,
              d.display_order AS "displayOrder", d.active,
              (SELECT COUNT(*)::int FROM flashcards c WHERE c.deck_id = d.id AND c.active) AS "cardCount"
         FROM flashcard_decks d
         JOIN lessons l ON l.id = d.lesson_id
         JOIN topics t ON t.id = l.topic_id
         JOIN subjects s ON s.id = t.subject_id
        WHERE d.lesson_id = $1 AND d.active AND l.active AND t.active AND s.active
        ORDER BY d.display_order, d.id`,
      [lessonId],
    );
    return result.rows;
  }

  async findFlashcardDeck(deckId) {
    const result = await this.pool.query(
      `SELECT d.id, d.lesson_id AS "lessonId", d.slug, d.name, d.description,
              d.display_order AS "displayOrder", d.active,
              (SELECT COUNT(*)::int FROM flashcards c WHERE c.deck_id = d.id AND c.active) AS "cardCount"
         FROM flashcard_decks d
         JOIN lessons l ON l.id = d.lesson_id
         JOIN topics t ON t.id = l.topic_id
         JOIN subjects s ON s.id = t.subject_id
        WHERE d.id = $1 AND d.active AND l.active AND t.active AND s.active`,
      [deckId],
    );
    return result.rows[0] ?? null;
  }

  async listFlashcards(deckId) {
    const result = await this.pool.query(
      `SELECT id, deck_id AS "deckId", front, back, hint,
              display_order AS "displayOrder", active
         FROM flashcards
        WHERE deck_id = $1 AND active
        ORDER BY display_order, id`,
      [deckId],
    );
    return result.rows;
  }

  async findFlashcardForReview(cardId) {
    const result = await this.pool.query(
      `SELECT c.id, c.deck_id AS "deckId"
         FROM flashcards c
         JOIN flashcard_decks d ON d.id = c.deck_id
         JOIN lessons l ON l.id = d.lesson_id
         JOIN topics t ON t.id = l.topic_id
         JOIN subjects s ON s.id = t.subject_id
        WHERE c.id = $1 AND c.active AND d.active AND l.active AND t.active AND s.active`,
      [cardId],
    );
    return result.rows[0] ?? null;
  }

  async findFlashcardReviewState(userId, cardId) {
    const result = await this.pool.query(
      `${flashcardStateSelect} WHERE user_id = $1 AND card_id = $2`,
      [userId, cardId],
    );
    return flashcardStateFrom(result.rows[0]);
  }

  async findFlashcardReviewStates(userId, cardIds) {
    if (!cardIds.length) return [];
    const result = await this.pool.query(
      `${flashcardStateSelect} WHERE user_id = $1 AND card_id = ANY($2::uuid[])`,
      [userId, cardIds],
    );
    return result.rows.map(flashcardStateFrom);
  }

  async appendFlashcardReview({ review, payloadHash }) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');

      const inserted = await client.query(
        `INSERT INTO flashcard_reviews
           (id, user_id, card_id, outcome, reviewed_at, resulting_box, resulting_due_at, algorithm_version, idempotency_key, payload_hash)
         VALUES ($1, $2, $3, $4, $5::timestamptz, $6, $7::timestamptz, $8, $9, $10)
         ON CONFLICT (user_id, idempotency_key) DO NOTHING
         RETURNING id, accepted_at AS "acceptedAt"`,
        [
          review.id, review.userId, review.cardId, review.outcome, review.reviewedAt,
          review.resultingBox, review.resultingDueAt, review.algorithmVersion,
          review.idempotencyKey, payloadHash,
        ],
      );

      if (!inserted.rowCount) {
        const existing = await client.query(
          `SELECT id, payload_hash AS "payloadHash", card_id AS "cardId", accepted_at AS "acceptedAt"
             FROM flashcard_reviews
            WHERE user_id = $1 AND idempotency_key = $2`,
          [review.userId, review.idempotencyKey],
        );
        if (!existing.rowCount || existing.rows[0].payloadHash !== payloadHash) {
          await client.query('ROLLBACK');
          return { status: 'conflict' };
        }
        const replayedState = await client.query(
          `${flashcardStateSelect} WHERE user_id = $1 AND card_id = $2`,
          [review.userId, existing.rows[0].cardId],
        );
        await client.query('COMMIT');
        return {
          status: 'replayed',
          review: { id: existing.rows[0].id, acceptedAt: toIso(existing.rows[0].acceptedAt) },
          state: flashcardStateFrom(replayedState.rows[0]),
        };
      }

      const state = await client.query(
        `INSERT INTO flashcard_review_states
           (user_id, card_id, box, due_at, last_reviewed_at, total_reviews, known_reviews, algorithm_version, updated_at)
         VALUES ($1, $2, $3, $4::timestamptz, $5::timestamptz, 1, $6, $7, NOW())
         ON CONFLICT (user_id, card_id) DO UPDATE SET
           box = EXCLUDED.box,
           due_at = EXCLUDED.due_at,
           last_reviewed_at = EXCLUDED.last_reviewed_at,
           total_reviews = flashcard_review_states.total_reviews + 1,
           known_reviews = flashcard_review_states.known_reviews + $6,
           algorithm_version = EXCLUDED.algorithm_version,
           updated_at = NOW()
         RETURNING card_id AS "cardId", box, due_at AS "dueAt", last_reviewed_at AS "lastReviewedAt",
                   total_reviews AS "totalReviews", known_reviews AS "knownReviews",
                   algorithm_version AS "algorithmVersion"`,
        [
          review.userId, review.cardId, review.resultingBox, review.resultingDueAt,
          review.reviewedAt, review.outcome === 'known' ? 1 : 0, review.algorithmVersion,
        ],
      );

      await client.query('COMMIT');
      return {
        status: 'created',
        review: { id: inserted.rows[0].id, acceptedAt: toIso(inserted.rows[0].acceptedAt) },
        state: flashcardStateFrom(state.rows[0]),
      };
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  async resetFlashcardDeckProgress(userId, deckId) {
    const result = await this.pool.query(
      `DELETE FROM flashcard_review_states
        WHERE user_id = $1
          AND card_id IN (SELECT id FROM flashcards WHERE deck_id = $2)`,
      [userId, deckId],
    );
    return result.rowCount;
  }

  // Reuses the existing (user_id, occurred_at) index — no new index needed.
  async listCompletionEvents(userId) {
    const result = await this.pool.query(`${eventSelect} WHERE user_id = $1 AND event_type = 'lesson.completed' ORDER BY occurred_at, id`, [userId]);
    return result.rows.map(eventFrom);
  }

  async getProgressSource(userId) {
    const [catalog, events] = await Promise.all([
      this.pool.query(`SELECT s.id AS "subjectId", t.id AS "topicId", l.id AS "lessonId" FROM subjects s LEFT JOIN topics t ON t.subject_id = s.id AND t.active = TRUE LEFT JOIN lessons l ON l.topic_id = t.id AND l.active = TRUE WHERE s.active = TRUE ORDER BY s.display_order, t.display_order, l.display_order`),
      this.pool.query(`${eventSelect} WHERE user_id = $1 ORDER BY occurred_at, id`, [userId]),
    ]);
    return { catalog: catalog.rows, events: events.rows.map(eventFrom) };
  }

  async getCampaignSource(userId) {
    const [subjects, topics, lessons, events] = await Promise.all([
      this.pool.query(`${subjectSelect} ORDER BY display_order, id`),
      this.pool.query(`${topicSelect} ORDER BY display_order, id`),
      this.pool.query(`${lessonSelect} ORDER BY display_order, id`),
      this.pool.query(`${eventSelect} WHERE user_id = $1 AND event_type = 'lesson.completed' ORDER BY occurred_at, id`, [userId]),
    ]);
    return {
      subjects: subjects.rows.map(subjectFrom),
      topics: topics.rows.map(topicFrom),
      lessons: lessons.rows.map(lessonFrom),
      events: events.rows.map(eventFrom),
    };
  }

  // Persistence primitives only. Eligibility and claim orchestration belong to a later service slice.
  async findStreakRecoveryByIdempotency({ userId, idempotencyKey, executor = null }) {
    const result = await (executor ?? this.pool).query(
      `SELECT id, user_id AS "userId", policy_version AS "policyVersion", missed_local_date::text AS "missedLocalDate",
              timezone, qualifying_action_type AS "qualifyingActionType", qualifying_action_id AS "qualifyingActionId",
              idempotency_key AS "idempotencyKey", payload_hash AS "payloadHash", accepted_at AS "acceptedAt"
       FROM streak_recoveries
       WHERE user_id = $1 AND idempotency_key = $2`,
      [userId, idempotencyKey],
    );
    return streakRecoveryFrom(result.rows[0]);
  }

  async findStreakRecoveryByMissedLocalDate({ userId, missedLocalDate, executor = null }) {
    const result = await (executor ?? this.pool).query(
      `SELECT id, user_id AS "userId", policy_version AS "policyVersion", missed_local_date::text AS "missedLocalDate",
              timezone, qualifying_action_type AS "qualifyingActionType", qualifying_action_id AS "qualifyingActionId",
              idempotency_key AS "idempotencyKey", payload_hash AS "payloadHash", accepted_at AS "acceptedAt"
       FROM streak_recoveries
       WHERE user_id = $1 AND missed_local_date = $2`,
      [userId, missedLocalDate],
    );
    return streakRecoveryFrom(result.rows[0]);
  }

  async listStreakRecoveries({ userId, acceptedAtOrAfter = null, executor = null }) {
    const result = await (executor ?? this.pool).query(
      `SELECT id, user_id AS "userId", policy_version AS "policyVersion", missed_local_date::text AS "missedLocalDate",
              timezone, qualifying_action_type AS "qualifyingActionType", qualifying_action_id AS "qualifyingActionId",
              idempotency_key AS "idempotencyKey", payload_hash AS "payloadHash", accepted_at AS "acceptedAt"
       FROM streak_recoveries
       WHERE user_id = $1
         AND ($2::timestamptz IS NULL OR accepted_at >= $2)
       ORDER BY accepted_at DESC, id DESC`,
      [userId, acceptedAtOrAfter],
    );
    return result.rows.map(streakRecoveryFrom);
  }

  async getStreakRecoveryEligibilitySource(userId, { executor = null } = {}) {
    const queryable = executor ?? this.pool;
    const [settings, actions] = await Promise.all([
      queryable.query('SELECT timezone, updated_at AS "updatedAt" FROM user_settings WHERE user_id = $1', [userId]),
      queryable.query(`SELECT action_type AS "actionType", action_id AS "actionId", accepted_at AS "acceptedAt"
        FROM (
          SELECT 'learning_event'::text AS action_type, id AS action_id, accepted_at
          FROM learning_events WHERE user_id = $1 AND event_type = 'lesson.completed'
          UNION ALL
          SELECT 'quiz_attempt'::text, id, accepted_at FROM quiz_attempts WHERE user_id = $1
          UNION ALL
          SELECT 'flashcard_review'::text, id, accepted_at FROM flashcard_reviews WHERE user_id = $1
        ) accepted_actions
        ORDER BY accepted_at, action_id`, [userId]),
    ]);
    if (!settings.rowCount) return null;
    return {
      timezone: settings.rows[0].timezone,
      timezoneEffectiveAt: toIso(settings.rows[0].updatedAt),
      actions: actions.rows.map((row) => ({ ...row, acceptedAt: toIso(row.acceptedAt) })),
    };
  }

  async createStreakRecovery({ userId, policyVersion, missedLocalDate, timezone, qualifyingActionType, qualifyingActionId, idempotencyKey, payloadHash, acceptedAt = null, executor = null }) {
    const result = await (executor ?? this.pool).query(
      `INSERT INTO streak_recoveries (
         user_id, policy_version, missed_local_date, timezone, qualifying_action_type, qualifying_action_id,
         idempotency_key, payload_hash, accepted_at
       ) VALUES ($1, $2, $3::date, $4, $5, $6, $7, $8, COALESCE($9::timestamptz, NOW()))
       ON CONFLICT DO NOTHING
       RETURNING id, user_id AS "userId", policy_version AS "policyVersion", missed_local_date::text AS "missedLocalDate",
                 timezone, qualifying_action_type AS "qualifyingActionType", qualifying_action_id AS "qualifyingActionId",
                 idempotency_key AS "idempotencyKey", payload_hash AS "payloadHash", accepted_at AS "acceptedAt"`,
      [userId, policyVersion, missedLocalDate, timezone, qualifyingActionType, qualifyingActionId, idempotencyKey, payloadHash, acceptedAt],
    );
    return streakRecoveryFrom(result.rows[0]);
  }

  async withStreakRecoveryClaimTransaction(userId, callback) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      await client.query('SELECT pg_advisory_xact_lock(hashtextextended($1, 93771))', [userId]);
      const result = await callback(client);
      await client.query('COMMIT');
      return result;
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  async createQuizAttempt({ attempt, answers, payloadHash }) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const inserted = await client.query(
        `INSERT INTO quiz_attempts (id, user_id, quiz_id, submitted_at, total_questions, correct_answers, score_percentage, idempotency_key, payload_hash, result)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
         ON CONFLICT (user_id, idempotency_key) DO NOTHING
         RETURNING id`,
        [attempt.id, attempt.userId, attempt.quizId, attempt.submittedAt, attempt.totalQuestions, attempt.correctAnswers, attempt.scorePercentage, attempt.idempotencyKey, payloadHash, JSON.stringify(attempt.result)],
      );
      if (inserted.rowCount) {
        for (const answer of answers) {
          await client.query(
            'INSERT INTO quiz_attempt_answers (attempt_id, question_id, selected_option_id, correct) VALUES ($1, $2, $3, $4)',
            [attempt.id, answer.questionId, answer.selectedOptionId, answer.correct],
          );
        }
        await client.query('COMMIT');
        return { status: 'created', result: attempt.result };
      }
      const existing = await client.query('SELECT payload_hash AS "payloadHash", result FROM quiz_attempts WHERE user_id = $1 AND idempotency_key = $2', [attempt.userId, attempt.idempotencyKey]);
      await client.query('COMMIT');
      if (!existing.rowCount || existing.rows[0].payloadHash !== payloadHash) return { status: 'conflict' };
      return { status: 'replayed', result: existing.rows[0].result };
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally { client.release(); }
  }

  async listWrongAnswers({ userId, page, pageSize, lessonId, quizId }) {
    const offset = (page - 1) * pageSize;
    const result = await this.pool.query(
      `SELECT qq.id AS "questionId", q.id AS "quizId", q.title AS "quizTitle",
              q.lesson_id AS "lessonId", qq.prompt,
              (ARRAY_AGG(selected.id ORDER BY qa.submitted_at DESC))[1] AS "selectedOptionId",
              (ARRAY_AGG(selected.text ORDER BY qa.submitted_at DESC))[1] AS "selectedOptionText",
              correct.id AS "correctOptionId", correct.text AS "correctOptionText",
              MAX(qa.submitted_at) AS "lastAnsweredAt", COUNT(*)::int AS "wrongCount",
              COUNT(*) OVER()::int AS "totalItems"
       FROM quiz_attempt_answers qaa
       JOIN quiz_attempts qa ON qa.id = qaa.attempt_id
       JOIN quiz_questions qq ON qq.id = qaa.question_id
       JOIN quizzes q ON q.id = qa.quiz_id
       JOIN quiz_answer_options selected ON selected.id = qaa.selected_option_id
       JOIN quiz_answer_options correct ON correct.question_id = qq.id AND correct.correct = TRUE
       WHERE qa.user_id = $1 AND qaa.correct = FALSE
         AND ($2::uuid IS NULL OR q.lesson_id = $2)
         AND ($3::uuid IS NULL OR q.id = $3)
       GROUP BY qq.id, q.id, q.title, q.lesson_id, qq.prompt, correct.id, correct.text
       ORDER BY "lastAnsweredAt" DESC, qq.id
       LIMIT $4 OFFSET $5`,
      [userId, lessonId ?? null, quizId ?? null, pageSize, offset],
    );
    const totalItems = result.rows[0]?.totalItems ?? 0;
    const items = result.rows.map(({ totalItems: _totalItems, lastAnsweredAt, ...row }) => ({
      ...row,
      lastAnsweredAt: toIso(lastAnsweredAt),
    }));
    return { items, page, pageSize, totalItems, hasNext: offset + items.length < totalItems };
  }

  async loadBossChallenge(challengeId, includeCorrect) {
    const challengeResult = await this.pool.query(
      `SELECT id, zone_id AS "zoneId", title, description,
              passing_percentage::float8 AS "passingPercentage",
              reward_shells AS "rewardShells", active
       FROM boss_challenges
       WHERE active = TRUE AND ($1::uuid IS NULL OR id = $1)
       ORDER BY created_at, id LIMIT 1`,
      [challengeId ?? null],
    );
    const challenge = challengeResult.rows[0];
    if (!challenge) return null;
    const [questionsResult, optionsResult] = await Promise.all([
      this.pool.query(
        `SELECT id, prompt, display_order AS "displayOrder"
         FROM boss_questions WHERE challenge_id = $1 ORDER BY display_order, id`,
        [challenge.id],
      ),
      this.pool.query(
        `SELECT o.id, o.question_id AS "questionId", o.text,
                o.display_order AS "displayOrder", o.correct
         FROM boss_answer_options o
         JOIN boss_questions q ON q.id = o.question_id
         WHERE q.challenge_id = $1
         ORDER BY q.display_order, o.display_order, o.id`,
        [challenge.id],
      ),
    ]);
    const questions = questionsResult.rows.map((question) => ({
      ...question,
      options: optionsResult.rows
        .filter((option) => option.questionId === question.id)
        .map((option) => includeCorrect ? option : {
          id: option.id, text: option.text, displayOrder: option.displayOrder,
        }),
    }));
    return { ...challenge, questionCount: questions.length, available: true, questions };
  }

  async findActiveBossChallenge() {
    return this.loadBossChallenge(null, false);
  }

  async findBossChallengeSource(challengeId) {
    return this.loadBossChallenge(challengeId, true);
  }

  async createBossAttempt({ attempt, answers, rewardShells, payloadHash }) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      await client.query('SELECT pg_advisory_xact_lock(hashtextextended($1::text, 0))', [attempt.userId]);
      const existing = await client.query(
        `SELECT payload_hash AS "payloadHash", result
         FROM boss_attempts WHERE user_id = $1 AND idempotency_key = $2`,
        [attempt.userId, attempt.idempotencyKey],
      );
      if (existing.rowCount) {
        await client.query('COMMIT');
        if (existing.rows[0].payloadHash !== payloadHash) return { status: 'conflict' };
        return { status: 'replayed', result: existing.rows[0].result };
      }
      await client.query(
        `INSERT INTO boss_attempts
           (id, user_id, challenge_id, submitted_at, total_questions, correct_answers,
            score_percentage, passed, reward_shells, idempotency_key, payload_hash, result)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,0,$9,$10,'{}'::jsonb)`,
        [attempt.id, attempt.userId, attempt.challengeId, attempt.submittedAt,
          attempt.totalQuestions, attempt.correctAnswers, attempt.scorePercentage,
          attempt.passed, attempt.idempotencyKey, payloadHash],
      );
      for (const answer of answers) {
        await client.query(
          `INSERT INTO boss_attempt_answers
             (attempt_id, question_id, selected_option_id, correct)
           VALUES ($1,$2,$3,$4)`,
          [attempt.id, answer.questionId, answer.selectedOptionId, answer.correct],
        );
      }
      await client.query(
        `INSERT INTO learner_wallets (user_id) VALUES ($1)
         ON CONFLICT (user_id) DO NOTHING`,
        [attempt.userId],
      );
      let awarded = 0;
      if (rewardShells > 0) {
        const award = await client.query(
          `INSERT INTO wallet_transactions
             (id, user_id, amount, transaction_type, reference_id)
           VALUES ($1,$2,$3,'boss_reward',$4)
           ON CONFLICT (user_id, transaction_type, reference_id) DO NOTHING
           RETURNING id`,
          [attempt.id, attempt.userId, rewardShells, attempt.challengeId],
        );
        if (award.rowCount) {
          awarded = rewardShells;
          await client.query(
            `UPDATE learner_wallets SET balance = balance + $2, updated_at = NOW()
             WHERE user_id = $1`,
            [attempt.userId, awarded],
          );
        }
      }
      const wallet = await client.query(
        'SELECT balance FROM learner_wallets WHERE user_id = $1',
        [attempt.userId],
      );
      const result = {
        attemptId: attempt.id,
        challengeId: attempt.challengeId,
        submittedAt: attempt.submittedAt,
        totalQuestions: attempt.totalQuestions,
        correctAnswers: attempt.correctAnswers,
        scorePercentage: attempt.scorePercentage,
        passed: attempt.passed,
        rewardShells: awarded,
        walletBalance: wallet.rows[0].balance,
      };
      await client.query(
        'UPDATE boss_attempts SET reward_shells = $2, result = $3 WHERE id = $1',
        [attempt.id, awarded, JSON.stringify(result)],
      );
      await client.query('COMMIT');
      return { status: 'created', result };
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  async getEconomy(userId) {
    await this.pool.query(
      `INSERT INTO learner_wallets (user_id) VALUES ($1)
       ON CONFLICT (user_id) DO NOTHING`,
      [userId],
    );
    const [wallet, items, inventory] = await Promise.all([
      this.pool.query(
        'SELECT currency, balance FROM learner_wallets WHERE user_id = $1',
        [userId],
      ),
      this.pool.query(
        `SELECT s.id, s.name, s.description, s.price_shells AS "priceShells",
                s.available, (i.item_id IS NOT NULL) AS owned
         FROM shop_items s
         LEFT JOIN learner_inventory i ON i.item_id = s.id AND i.user_id = $1
         ORDER BY s.created_at, s.id`,
        [userId],
      ),
      this.pool.query(
        `SELECT item_id AS "itemId", quantity, equipped
         FROM learner_inventory WHERE user_id = $1
         ORDER BY acquired_at, item_id`,
        [userId],
      ),
    ]);
    return {
      currency: wallet.rows[0].currency,
      balance: wallet.rows[0].balance,
      shopItems: items.rows,
      inventory: inventory.rows,
    };
  }

  async createShopPurchase({ purchaseId, userId, itemId, idempotencyKey, payloadHash, purchasedAt }) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      await client.query('SELECT pg_advisory_xact_lock(hashtextextended($1::text, 0))', [userId]);
      const existing = await client.query(
        `SELECT payload_hash AS "payloadHash", result
         FROM shop_purchases WHERE user_id = $1 AND idempotency_key = $2`,
        [userId, idempotencyKey],
      );
      if (existing.rowCount) {
        await client.query('COMMIT');
        if (existing.rows[0].payloadHash !== payloadHash) return { status: 'conflict' };
        return { status: 'replayed', result: existing.rows[0].result };
      }
      const itemResult = await client.query(
        `SELECT id, price_shells AS "priceShells", available
         FROM shop_items WHERE id = $1`,
        [itemId],
      );
      if (!itemResult.rowCount) {
        await client.query('ROLLBACK');
        return { status: 'not_found' };
      }
      const item = itemResult.rows[0];
      if (!item.available) {
        await client.query('ROLLBACK');
        return { status: 'unavailable' };
      }
      const owned = await client.query(
        'SELECT 1 FROM learner_inventory WHERE user_id = $1 AND item_id = $2',
        [userId, itemId],
      );
      if (owned.rowCount) {
        await client.query('ROLLBACK');
        return { status: 'owned' };
      }
      await client.query(
        `INSERT INTO learner_wallets (user_id) VALUES ($1)
         ON CONFLICT (user_id) DO NOTHING`,
        [userId],
      );
      const wallet = await client.query(
        'SELECT balance FROM learner_wallets WHERE user_id = $1 FOR UPDATE',
        [userId],
      );
      if (wallet.rows[0].balance < item.priceShells) {
        await client.query('ROLLBACK');
        return { status: 'insufficient_balance' };
      }
      const balance = wallet.rows[0].balance - item.priceShells;
      await client.query(
        `UPDATE learner_wallets SET balance = $2, updated_at = NOW()
         WHERE user_id = $1`,
        [userId, balance],
      );
      await client.query(
        `INSERT INTO learner_inventory (user_id, item_id, quantity, equipped)
         VALUES ($1,$2,1,FALSE)`,
        [userId, itemId],
      );
      const result = {
        purchaseId,
        itemId,
        priceShells: item.priceShells,
        balance,
        inventoryQuantity: 1,
        purchasedAt,
      };
      await client.query(
        `INSERT INTO shop_purchases
           (id, user_id, item_id, price_shells, idempotency_key, payload_hash, result, purchased_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
        [purchaseId, userId, itemId, item.priceShells, idempotencyKey,
          payloadHash, JSON.stringify(result), purchasedAt],
      );
      await client.query(
        `INSERT INTO wallet_transactions
           (id, user_id, amount, transaction_type, reference_id)
         VALUES ($1,$2,$3,'shop_purchase',$1)`,
        [purchaseId, userId, -item.priceShells],
      );
      await client.query('COMMIT');
      return { status: 'created', result };
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  // ── P5-01B AI Tutor repository methods ──

  async findTutorLessonContext(lessonId) {
    const result = await this.pool.query(
      `SELECT l.id AS "lessonId", l.title, l.description, l.difficulty,
              s.name AS "subjectName", t.name AS "topicName"
       FROM lessons l
       JOIN topics t ON t.id = l.topic_id
       JOIN subjects s ON s.id = t.subject_id
       WHERE l.id = $1
         AND l.active = TRUE
         AND t.active = TRUE
         AND s.active = TRUE`,
      [lessonId],
    );
    return result.rowCount ? result.rows[0] : null;
  }

  async admitTutorProviderCall({ userId, idempotencyKey, requestFingerprint, lessonId, now, concurrencyLimit, leaseDurationSeconds }) {
    const leaseDuration = `${leaseDurationSeconds} seconds`;
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');

      // 1. Acquire per-user advisory transaction lock
      const lockSeed = 5567942638328492081n;
      await client.query(
        'SELECT pg_advisory_xact_lock(hashtextextended($1::text, $2::bigint))',
        [userId, lockSeed.toString()],
      );

      // 2. Opportunistic cleanup
      await client.query(
        `DELETE FROM ai_tutor_idempotency
         WHERE state = 'completed' AND expires_at < $1`,
        [now],
      );
      await client.query(
        // $1 is cast explicitly: without it PostgreSQL infers the parameter's
        // type from `$1 - INTERVAL`, settles on `interval - interval`, and the
        // comparison becomes `timestamptz < interval`, which has no operator.
        `DELETE FROM ai_tutor_idempotency
         WHERE state = 'processing'
           AND lease_expires_at < ($1::timestamptz - INTERVAL '5 minutes')`,
        [now],
      );

      // 3. SELECT existing row for idempotency inspection
      const existing = await client.query(
        `SELECT id, request_fingerprint, state, lease_expires_at, claim_token, normalized_response
         FROM ai_tutor_idempotency
         WHERE user_id = $1 AND idempotency_key = $2
         FOR UPDATE`,
        [userId, idempotencyKey],
      );

      if (existing.rowCount) {
        const row = existing.rows[0];

        // Fingerprint-first: mismatch → conflict
        if (row.request_fingerprint !== requestFingerprint) {
          await client.query('COMMIT');
          return { outcome: 'fingerprintMismatch' };
        }

        // Completed → replay
        if (row.state === 'completed') {
          await client.query('COMMIT');
          return {
            outcome: 'completedReplay',
            result: row.normalized_response,
          };
        }

        // Processing + lease not expired → active
        if (new Date(row.lease_expires_at) > new Date(now)) {
          await client.query('COMMIT');
          return { outcome: 'activeProcessing' };
        }

        // Stale processing row — candidate for reclaim
        const candidateIdempotencyKey = idempotencyKey;

        // 4. Count other active processing rows (exclude candidate)
        const concurrency = await client.query(
          `SELECT COUNT(*)::int AS active_count
           FROM ai_tutor_idempotency
           WHERE user_id = $1
             AND state = 'processing'
             AND lease_expires_at > $2
             AND idempotency_key <> $3`,
          [userId, now, candidateIdempotencyKey],
        );

        if (concurrency.rows[0].active_count >= concurrencyLimit) {
          await client.query('COMMIT');
          return { outcome: 'concurrencyRejected' };
        }

        // 5. Reclaim the stale row
        const reclaimed = await client.query(
          `UPDATE ai_tutor_idempotency
           SET claim_token = gen_random_uuid(),
               processing_started_at = $3::timestamptz,
               lease_expires_at = $3::timestamptz + INTERVAL '${leaseDuration}'
           WHERE user_id = $1 AND idempotency_key = $2 AND state = 'processing'
           RETURNING id, idempotency_key AS "idempotencyKey", lesson_id AS "lessonId", claim_token AS "claimToken", created_at AS "createdAt"`,
          [userId, idempotencyKey, now],
        );

        await client.query('COMMIT');
        return {
          outcome: 'claimReclaimed',
          row: {
            id: reclaimed.rows[0].id,
            idempotencyKey: reclaimed.rows[0].idempotencyKey,
            claimToken: reclaimed.rows[0].claimToken,
            lessonId: reclaimed.rows[0].lessonId,
            createdAt: reclaimed.rows[0].createdAt,
          },
        };
      }

      // No existing row → need to evaluate concurrency for new claim
      const concurrency = await client.query(
        `SELECT COUNT(*)::int AS active_count
         FROM ai_tutor_idempotency
         WHERE user_id = $1
           AND state = 'processing'
           AND lease_expires_at > $2`,
        [userId, now],
      );

      if (concurrency.rows[0].active_count >= concurrencyLimit) {
        await client.query('COMMIT');
        return { outcome: 'concurrencyRejected' };
      }

      // 6. INSERT new claim
      const inserted = await client.query(
        `INSERT INTO ai_tutor_idempotency
         (id, user_id, idempotency_key, request_fingerprint, lesson_id, state,
          processing_started_at, lease_expires_at, claim_token, created_at, expires_at)
         VALUES (gen_random_uuid(), $1, $2, $3, $4, 'processing',
                 $5::timestamptz, $5::timestamptz + INTERVAL '${leaseDuration}', gen_random_uuid(),
                 $5::timestamptz, $5::timestamptz + INTERVAL '24 hours')
         ON CONFLICT (user_id, idempotency_key) DO NOTHING
         RETURNING id, idempotency_key AS "idempotencyKey", lesson_id AS "lessonId", claim_token AS "claimToken", created_at AS "createdAt"`,
        [userId, idempotencyKey, requestFingerprint, lessonId, now],
      );

      if (inserted.rowCount) {
        await client.query('COMMIT');
        return {
          outcome: 'claimCreated',
          row: {
            id: inserted.rows[0].id,
            idempotencyKey: inserted.rows[0].idempotencyKey,
            claimToken: inserted.rows[0].claimToken,
            lessonId: inserted.rows[0].lessonId,
            createdAt: inserted.rows[0].createdAt,
          },
        };
      }

      // Race: another request inserted the same key between SELECT and INSERT
      // Re-read and classify
      const recheck = await client.query(
        `SELECT state, request_fingerprint, normalized_response
         FROM ai_tutor_idempotency
         WHERE user_id = $1 AND idempotency_key = $2
         FOR UPDATE`,
        [userId, idempotencyKey],
      );

      if (!recheck.rowCount) {
        await client.query('COMMIT');
        return { outcome: 'concurrencyRejected' };
      }

      if (recheck.rows[0].request_fingerprint !== requestFingerprint) {
        await client.query('COMMIT');
        return { outcome: 'fingerprintMismatch' };
      }

      if (recheck.rows[0].state === 'completed') {
        await client.query('COMMIT');
        return { outcome: 'completedReplay', result: recheck.rows[0].normalized_response };
      }

      await client.query('COMMIT');
      return { outcome: 'activeProcessing' };
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  async completeTutorRequest({ userId, idempotencyKey, claimToken, answer, status, now }) {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');

      const found = await client.query(
        `SELECT id, lesson_id AS "lessonId"
         FROM ai_tutor_idempotency
         WHERE user_id = $1
           AND idempotency_key = $2
           AND claim_token = $3
           AND state = 'processing'
           AND lease_expires_at > $4
         FOR UPDATE`,
        [userId, idempotencyKey, claimToken, now],
      );

      if (!found.rowCount) {
        await client.query('COMMIT');
        return null;
      }

      const row = found.rows[0];
      const normalizedResponse = JSON.stringify({
        responseId: row.id,
        lessonId: row.lessonId,
        answer,
        createdAt: new Date(now).toISOString(),
        status,
      });

      await client.query(
        `UPDATE ai_tutor_idempotency
         SET normalized_response = $3::jsonb,
             state = 'completed',
             completed_at = $4
         WHERE user_id = $1 AND idempotency_key = $2`,
        [userId, idempotencyKey, normalizedResponse, now],
      );

      await client.query('COMMIT');
      return {
        outcome: 'completed',
        result: JSON.parse(normalizedResponse),
      };
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    } finally {
      client.release();
    }
  }

  async releaseTutorRequest({ userId, idempotencyKey, claimToken }) {
    const result = await this.pool.query(
      `DELETE FROM ai_tutor_idempotency
       WHERE user_id = $1
         AND idempotency_key = $2
         AND claim_token = $3
         AND state = 'processing'`,
      [userId, idempotencyKey, claimToken],
    );
    return result.rowCount ? { outcome: 'released' } : null;
  }

  async deleteExpiredTutorRecords(now) {
    await this.pool.query(
      `DELETE FROM ai_tutor_idempotency
       WHERE state = 'completed' AND expires_at < $1`,
      [now],
    );
    await this.pool.query(
      `DELETE FROM ai_tutor_idempotency
       WHERE state = 'processing'
         AND lease_expires_at < ($1::timestamptz - INTERVAL '5 minutes')`,
      [now],
    );
  }
}
