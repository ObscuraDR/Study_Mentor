import { validationError } from './errors.js';

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/u;
const educationLevels = new Set(['beginner', 'intermediate', 'advanced']);
const uuidV7Pattern = /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/u;
const rfc3339Pattern = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/u;

function object(body, allowed, required = []) {
  if (!body || Array.isArray(body) || typeof body !== 'object') throw validationError([{ field: 'body', reason: 'must_be_an_object' }]);
  const invalid = Object.keys(body).filter((key) => !allowed.includes(key));
  const missing = required.filter((key) => body[key] === undefined);
  if (invalid.length || missing.length) throw validationError([...invalid.map((field) => ({ field, reason: 'unknown_field' })), ...missing.map((field) => ({ field, reason: 'required' }))]);
}

function string(value, field, { minimum = 1, maximum, trim = true } = {}) {
  if (typeof value !== 'string') throw validationError([{ field, reason: 'must_be_a_string' }]);
  const normalized = trim ? value.trim() : value;
  if (normalized.length < minimum || (maximum && normalized.length > maximum) || /[\u0000-\u001f\u007f]/u.test(normalized)) throw validationError([{ field, reason: 'invalid_length_or_characters' }]);
  return normalized;
}

export function clientPlatform(req) {
  const platform = req.get('X-Client-Platform');
  if (platform === 'web' || platform === 'android') return platform;
  throw validationError([{ field: 'X-Client-Platform', reason: 'must_be_web_or_android' }]);
}

export function registerRequest(body) {
  object(body, ['displayName', 'email', 'password'], ['displayName', 'email', 'password']);
  const displayName = string(body.displayName, 'displayName', { maximum: 120 });
  const email = string(body.email, 'email', { maximum: 320 }).toLowerCase();
  const password = string(body.password, 'password', { minimum: 8, maximum: 256, trim: false });
  if (!emailPattern.test(email)) throw validationError([{ field: 'email', reason: 'invalid_format' }]);
  return { displayName, email, password };
}

export function loginRequest(body) {
  object(body, ['email', 'password'], ['email', 'password']);
  const email = string(body.email, 'email', { maximum: 320 }).toLowerCase();
  const password = string(body.password, 'password', { minimum: 1, maximum: 256, trim: false });
  if (!emailPattern.test(email)) throw validationError([{ field: 'email', reason: 'invalid_format' }]);
  return { email, password };
}

export function updateProfileRequest(body) {
  object(body, ['displayName', 'avatarKey', 'educationLevel']);
  if (!Object.keys(body).length) throw validationError([{ field: 'body', reason: 'must_not_be_empty' }]);
  const patch = {};
  if (body.displayName !== undefined) patch.displayName = string(body.displayName, 'displayName', { maximum: 120 });
  if (body.avatarKey !== undefined) patch.avatarKey = body.avatarKey === null ? null : string(body.avatarKey, 'avatarKey', { maximum: 80 });
  if (body.educationLevel !== undefined) {
    if (body.educationLevel !== null && !educationLevels.has(body.educationLevel)) throw validationError([{ field: 'educationLevel', reason: 'invalid_value' }]);
    patch.educationLevel = body.educationLevel;
  }
  return patch;
}

export function replaceSettingsRequest(body) {
  // Timezone is additive. Older clients may still replace their two existing
  // settings fields; in that case the persisted backend-owned timezone remains.
  object(body, ['locale', 'dailyGoalTargetXp', 'timezone'], ['locale', 'dailyGoalTargetXp']);
  if (body.locale !== 'vi' && body.locale !== 'en') throw validationError([{ field: 'locale', reason: 'invalid_value' }]);
  if (!Number.isInteger(body.dailyGoalTargetXp) || body.dailyGoalTargetXp < 1 || body.dailyGoalTargetXp > 10_000) {
    throw validationError([{ field: 'dailyGoalTargetXp', reason: 'must_be_an_integer_between_1_and_10000' }]);
  }
  if (body.timezone !== undefined) {
    if (typeof body.timezone !== 'string' || body.timezone.length > 64) throw validationError([{ field: 'timezone', reason: 'invalid_value' }]);
    try { new Intl.DateTimeFormat('en-US', { timeZone: body.timezone }); } catch { throw validationError([{ field: 'timezone', reason: 'invalid_value' }]); }
  }
  return { locale: body.locale, dailyGoalTargetXp: body.dailyGoalTargetXp, timezone: body.timezone };
}

export function idempotencyKey(req) {
  const value = req.get('Idempotency-Key');
  if (!value || !uuidV7Pattern.test(value)) throw validationError([{ field: 'Idempotency-Key', reason: 'must_be_a_uuidv7' }]);
  return value;
}

export function emptyRequestBody(body) {
  if (body === undefined) return;
  object(body, []);
  if (Object.keys(body).length) throw validationError(Object.keys(body).map((field) => ({ field, reason: 'unknown_field' })));
}

export function learningEventRequest(body, now, futureToleranceSeconds) {
  object(body, ['lessonId', 'occurredAt', 'durationSeconds', 'eventType'], ['lessonId', 'occurredAt', 'durationSeconds', 'eventType']);
  if (typeof body.lessonId !== 'string' || !uuidV7Pattern.test(body.lessonId)) throw validationError([{ field: 'lessonId', reason: 'must_be_a_uuidv7' }]);
  if (typeof body.occurredAt !== 'string' || !rfc3339Pattern.test(body.occurredAt) || Number.isNaN(Date.parse(body.occurredAt))) throw validationError([{ field: 'occurredAt', reason: 'must_be_an_rfc3339_timestamp' }]);
  const occurredAt = new Date(body.occurredAt);
  if (occurredAt.getTime() > now.getTime() + futureToleranceSeconds * 1000) throw validationError([{ field: 'occurredAt', reason: 'must_not_be_beyond_future_tolerance' }]);
  if (!Number.isSafeInteger(body.durationSeconds) || body.durationSeconds < 0) throw validationError([{ field: 'durationSeconds', reason: 'must_be_a_non_negative_integer' }]);
  if (body.eventType !== 'lesson.completed') throw validationError([{ field: 'eventType', reason: 'invalid_value' }]);
  return { lessonId: body.lessonId, occurredAt: occurredAt.toISOString(), durationSeconds: body.durationSeconds, eventType: body.eventType };
}

/**
 * A flashcard review submission.
 *
 * Only client-owned facts are accepted: which card, what happened, and when.
 * `additionalProperties: false` in the contract is enforced here by the strict
 * key list, so a client cannot smuggle a box, due date or XP value through.
 */
export function flashcardReviewRequest(body, now, futureToleranceSeconds) {
  object(body, ['cardId', 'outcome', 'reviewedAt'], ['cardId', 'outcome', 'reviewedAt']);
  if (typeof body.cardId !== 'string' || !uuidV7Pattern.test(body.cardId)) throw validationError([{ field: 'cardId', reason: 'must_be_a_uuidv7' }]);
  if (body.outcome !== 'known' && body.outcome !== 'forgot') throw validationError([{ field: 'outcome', reason: 'invalid_value' }]);
  if (typeof body.reviewedAt !== 'string' || !rfc3339Pattern.test(body.reviewedAt) || Number.isNaN(Date.parse(body.reviewedAt))) throw validationError([{ field: 'reviewedAt', reason: 'must_be_an_rfc3339_timestamp' }]);
  const reviewedAt = new Date(body.reviewedAt);
  if (reviewedAt.getTime() > now.getTime() + futureToleranceSeconds * 1000) throw validationError([{ field: 'reviewedAt', reason: 'must_not_be_beyond_future_tolerance' }]);
  return { cardId: body.cardId, outcome: body.outcome, reviewedAt: reviewedAt.toISOString() };
}

export function deckIdQuery(query) {
  if (!query || typeof query.deckId !== 'string' || !uuidV7Pattern.test(query.deckId)) throw validationError([{ field: 'deckId', reason: 'must_be_a_uuidv7' }]);
  const dueOnly = query.dueOnly === undefined ? true : query.dueOnly === 'true' || query.dueOnly === true;
  let limit = 20;
  if (query.limit !== undefined) {
    limit = Number(query.limit);
    if (!Number.isSafeInteger(limit) || limit < 1 || limit > 100) throw validationError([{ field: 'limit', reason: 'must_be_between_1_and_100' }]);
  }
  return { deckId: query.deckId, dueOnly, limit };
}

export function lessonIdQuery(query) {
  if (!query || typeof query.lessonId !== 'string' || !uuidV7Pattern.test(query.lessonId)) throw validationError([{ field: 'lessonId', reason: 'must_be_a_uuidv7' }]);
  return query.lessonId;
}

export function uuidPath(value, field) {
  if (typeof value !== 'string' || !uuidV7Pattern.test(value)) throw validationError([{ field, reason: 'must_be_a_uuidv7' }]);
  return value;
}

export function quizAttemptRequest(body) {
  object(body, ['quizId', 'answers'], ['quizId', 'answers']);
  if (typeof body.quizId !== 'string' || !uuidV7Pattern.test(body.quizId)) throw validationError([{ field: 'quizId', reason: 'must_be_a_uuidv7' }]);
  if (!Array.isArray(body.answers) || !body.answers.length) throw validationError([{ field: 'answers', reason: 'must_be_a_non_empty_array' }]);
  const answers = body.answers.map((answer, index) => {
    object(answer, ['questionId', 'selectedOptionId'], ['questionId', 'selectedOptionId']);
    if (typeof answer.questionId !== 'string' || !uuidV7Pattern.test(answer.questionId)) throw validationError([{ field: `answers[${index}].questionId`, reason: 'must_be_a_uuidv7' }]);
    if (typeof answer.selectedOptionId !== 'string' || !uuidV7Pattern.test(answer.selectedOptionId)) throw validationError([{ field: `answers[${index}].selectedOptionId`, reason: 'must_be_a_uuidv7' }]);
    return { questionId: answer.questionId, selectedOptionId: answer.selectedOptionId };
  });
  return { quizId: body.quizId, answers };
}

export function wrongAnswerQuery(query = {}) {
  const page = query.page === undefined ? 1 : Number(query.page);
  const pageSize = query.pageSize === undefined ? 20 : Number(query.pageSize);
  if (!Number.isSafeInteger(page) || page < 1) throw validationError([{ field: 'page', reason: 'must_be_a_positive_integer' }]);
  if (!Number.isSafeInteger(pageSize) || pageSize < 1 || pageSize > 100) throw validationError([{ field: 'pageSize', reason: 'must_be_between_1_and_100' }]);
  const lessonId = query.lessonId === undefined ? undefined : uuidPath(query.lessonId, 'lessonId');
  const quizId = query.quizId === undefined ? undefined : uuidPath(query.quizId, 'quizId');
  return { page, pageSize, lessonId, quizId };
}

export function bossAttemptRequest(body) {
  object(body, ['challengeId', 'answers'], ['challengeId', 'answers']);
  const challengeId = uuidPath(body.challengeId, 'challengeId');
  if (!Array.isArray(body.answers) || !body.answers.length) throw validationError([{ field: 'answers', reason: 'must_be_a_non_empty_array' }]);
  const answers = body.answers.map((answer, index) => {
    object(answer, ['questionId', 'selectedOptionId'], ['questionId', 'selectedOptionId']);
    return {
      questionId: uuidPath(answer.questionId, `answers[${index}].questionId`),
      selectedOptionId: uuidPath(answer.selectedOptionId, `answers[${index}].selectedOptionId`),
    };
  });
  return { challengeId, answers };
}

export function purchaseRequest(body) {
  object(body, ['itemId'], ['itemId']);
  return { itemId: uuidPath(body.itemId, 'itemId') };
}

export function tutorRequest(body) {
  object(body, ['lessonId', 'message'], ['lessonId', 'message']);
  if (typeof body.lessonId !== 'string' || !uuidV7Pattern.test(body.lessonId)) throw validationError([{ field: 'lessonId', reason: 'must_be_a_uuidv7' }]);
  const message = string(body.message, 'message', { maximum: 2000 });
  return { lessonId: body.lessonId, message };
}
