import assert from 'node:assert/strict';
import test, { after, before, beforeEach } from 'node:test';
import { v7 as uuidv7 } from 'uuid';
import { createApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import { applySchemaMigrations } from '../src/database/identity-migration.js';
import { PostgresIdentityRepository } from '../src/repositories/postgres-identity-repository.js';
import { startBackend } from '../src/server.js';
import { StreakRecoveryEligibilityService } from '../src/services/streak-recovery-eligibility-service.js';
import { StreakRecoveryClaimService } from '../src/services/streak-recovery-claim-service.js';
import { EngagementService } from '../src/services/engagement-service.js';
import { CampaignService } from '../src/services/campaign-service.js';

const databaseUrl = process.env.TEST_DATABASE_URL;
if (!databaseUrl) throw new Error('TEST_DATABASE_URL is required for PostgreSQL integration tests.');
if (!new URL(databaseUrl).pathname.replace(/^\//u, '').endsWith('_test')) throw new Error('TEST_DATABASE_URL must target a database whose name ends in _test.');

const config = loadConfig({
  environment: 'test',
  databaseUrl,
  jwtAccessSecret: process.env.JWT_ACCESS_SECRET ?? 'local-development-only-access-secret-at-least-32-characters',
});
const logger = { info() {}, warn() {}, error() {} };
const repository = new PostgresIdentityRepository(config.databaseUrl);
const app = createApp({ config, repository, logger, enableRateLimit: false });
let server;
let baseUrl;

async function request(path, { method = 'GET', headers = {}, body } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: { ...headers, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return { response, payload: response.status === 204 ? undefined : await response.json() };
}

async function register(email = `integration-${uuidv7()}@example.com`) {
  return request('/api/v1/auth/register', {
    method: 'POST', headers: { 'X-Client-Platform': 'android' },
    body: { displayName: 'Integration Student', email, password: 'Correct Horse Battery 1' },
  });
}

before(async () => {
  await repository.verifyConnection();
  await applySchemaMigrations(repository.pool);
  server = await new Promise((resolve) => { const current = app.listen(0, '127.0.0.1', () => resolve(current)); });
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

beforeEach(async () => {
  await repository.pool.query('TRUNCATE TABLE sessions, refresh_token_families, user_profiles, user_settings, users CASCADE');
});

after(async () => {
  await new Promise((resolve) => server.close(resolve));
  await repository.close();
});

test('migrations are idempotent and provision the complete Full Product v1 schema', async () => {
  await applySchemaMigrations(repository.pool);
  const tables = await repository.pool.query("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name");
  assert.deepEqual(tables.rows.map((row) => row.table_name), [
    'achievement_entitlements',
    'ai_tutor_idempotency',
    'boss_answer_options',
    'boss_attempt_answers',
    'boss_attempts',
    'boss_challenges',
    'boss_questions',
    'engagement_awards',
    'flashcard_decks',
    'flashcard_review_states',
    'flashcard_reviews',
    'flashcards',
    'learner_inventory',
    'learner_wallets',
    'learning_events',
    'lessons',
    'quiz_answer_options',
    'quiz_attempt_answers',
    'quiz_attempts',
    'quiz_questions',
    'quizzes',
    'refresh_token_families',
    'sessions',
    'shop_items',
    'shop_purchases',
    'streak_recoveries',
    'subjects',
    'topics',
    'user_profiles',
    'user_settings',
    'users',
    'wallet_transactions',
  ]);
  const hashColumn = await repository.pool.query("SELECT is_nullable, data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'sessions' AND column_name = 'refresh_token_hash'");
  assert.deepEqual(hashColumn.rows[0], { is_nullable: 'NO', data_type: 'character' });
  const plaintext = await repository.pool.query("SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'sessions' AND column_name = 'refresh_token'");
  assert.equal(plaintext.rowCount, 0);
  const settings = await repository.pool.query("SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'user_settings' ORDER BY column_name");
  assert.deepEqual(settings.rows.map((row) => row.column_name), ['created_at', 'daily_goal_target_xp', 'locale', 'revision', 'timezone', 'updated_at', 'user_id']);
  const catalog = await repository.pool.query('SELECT COUNT(*)::int AS subjects FROM subjects WHERE active');
  assert.equal(catalog.rows[0].subjects, 5);
  const eventColumns = await repository.pool.query("SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'learning_events' ORDER BY column_name");
  assert.deepEqual(eventColumns.rows.map((row) => row.column_name), ['accepted_at', 'duration_seconds', 'event_type', 'id', 'idempotency_key', 'lesson_id', 'occurred_at', 'payload_hash', 'user_id', 'xp_earned']);
  const quizCatalog = await repository.pool.query('SELECT COUNT(*)::int AS quizzes FROM quizzes WHERE active');
  assert.equal(quizCatalog.rows[0].quizzes, 25);
  const quizContent = await repository.pool.query(`
    SELECT
      (SELECT COUNT(*)::int FROM topics t JOIN subjects s ON s.id = t.subject_id WHERE s.active AND t.active) AS topics,
      (SELECT COUNT(*)::int FROM lessons l JOIN topics t ON t.id = l.topic_id JOIN subjects s ON s.id = t.subject_id WHERE s.active AND t.active AND l.active) AS lessons,
      (SELECT COUNT(*)::int FROM quiz_questions qq JOIN quizzes q ON q.id = qq.quiz_id JOIN lessons l ON l.id = q.lesson_id JOIN topics t ON t.id = l.topic_id JOIN subjects s ON s.id = t.subject_id WHERE s.active AND t.active AND l.active AND q.active) AS questions,
      (SELECT COUNT(*)::int FROM quiz_answer_options o JOIN quiz_questions qq ON qq.id = o.question_id JOIN quizzes q ON q.id = qq.quiz_id JOIN lessons l ON l.id = q.lesson_id JOIN topics t ON t.id = l.topic_id JOIN subjects s ON s.id = t.subject_id WHERE s.active AND t.active AND l.active AND q.active) AS options
  `);
  assert.deepEqual(quizContent.rows[0], { topics: 13, lessons: 25, questions: 390, options: 1560 });
  const malformed = await repository.pool.query(`
    SELECT COUNT(*)::int AS count FROM (
      SELECT qq.id FROM quiz_questions qq
      JOIN quizzes q ON q.id = qq.quiz_id JOIN lessons l ON l.id = q.lesson_id
      JOIN topics t ON t.id = l.topic_id JOIN subjects s ON s.id = t.subject_id
      JOIN quiz_answer_options o ON o.question_id = qq.id
      WHERE s.active AND t.active AND l.active AND q.active
      GROUP BY qq.id HAVING COUNT(*) <> 4 OR COUNT(*) FILTER (WHERE o.correct) <> 1
    ) invalid
  `);
  assert.equal(malformed.rows[0].count, 0);
  const recoveryColumns = await repository.pool.query("SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'streak_recoveries' ORDER BY column_name");
  assert.deepEqual(recoveryColumns.rows.map((row) => row.column_name), ['accepted_at', 'id', 'idempotency_key', 'missed_local_date', 'payload_hash', 'policy_version', 'qualifying_action_id', 'qualifying_action_type', 'timezone', 'user_id']);
  const fullProductCatalog = await repository.pool.query(`
    SELECT
      (SELECT COUNT(*)::int FROM boss_challenges WHERE active) AS bosses,
      (SELECT COUNT(*)::int FROM boss_questions) AS boss_questions,
      (SELECT COUNT(*)::int FROM boss_answer_options) AS boss_options,
      (SELECT COUNT(*)::int FROM shop_items WHERE available) AS shop_items
  `);
  assert.deepEqual(fullProductCatalog.rows[0], {
    bosses: 1,
    boss_questions: 2,
    boss_options: 4,
    shop_items: 2,
  });
});

test('private streak recovery repository primitives preserve audit evidence and unique claim boundaries', async () => {
  const userId = uuidv7();
  await repository.pool.query('INSERT INTO users (id, email, display_name, password_hash, role) VALUES ($1, $2, $3, $4, $5)', [userId, `recovery-${uuidv7()}@example.com`, 'Recovery Student', '$argon2id$placeholder', 'user']);
  const first = await repository.createStreakRecovery({
    userId,
    policyVersion: 'streak-recovery-v1',
    missedLocalDate: '2026-07-20',
    timezone: 'Asia/Ho_Chi_Minh',
    qualifyingActionType: 'learning_event',
    qualifyingActionId: uuidv7(),
    idempotencyKey: uuidv7(),
    payloadHash: 'a'.repeat(64),
    acceptedAt: '2026-07-21T09:00:00.000Z',
  });
  assert.ok(first?.id);
  assert.equal(first.missedLocalDate, '2026-07-20');
  assert.equal(first.timezone, 'Asia/Ho_Chi_Minh');
  assert.equal(first.qualifyingActionType, 'learning_event');
  assert.equal(first.acceptedAt, '2026-07-21T09:00:00.000Z');

  const byKey = await repository.findStreakRecoveryByIdempotency({ userId, idempotencyKey: first.idempotencyKey });
  const byDate = await repository.findStreakRecoveryByMissedLocalDate({ userId, missedLocalDate: '2026-07-20' });
  assert.deepEqual(byKey, first);
  assert.deepEqual(byDate, first);

  const duplicateDate = await repository.createStreakRecovery({ ...first, idempotencyKey: uuidv7(), qualifyingActionId: uuidv7(), payloadHash: 'b'.repeat(64) });
  const duplicateKey = await repository.createStreakRecovery({ ...first, missedLocalDate: '2026-07-22', qualifyingActionId: uuidv7(), payloadHash: 'c'.repeat(64) });
  const duplicateAction = await repository.createStreakRecovery({ ...first, missedLocalDate: '2026-07-22', idempotencyKey: uuidv7(), payloadHash: 'd'.repeat(64) });
  assert.equal(duplicateDate, null);
  assert.equal(duplicateKey, null);
  assert.equal(duplicateAction, null);

  const rollingWindow = await repository.listStreakRecoveries({ userId, acceptedAtOrAfter: '2026-06-21T09:00:00.000Z' });
  assert.deepEqual(rollingWindow, [first]);
  const afterWindow = await repository.listStreakRecoveries({ userId, acceptedAtOrAfter: '2026-07-21T09:00:01.000Z' });
  assert.deepEqual(afterWindow, []);
});

test('streak recovery eligibility projection reads accepted evidence without writing awards, entitlements, or recoveries', async () => {
  const userId = uuidv7();
  await repository.pool.query('INSERT INTO users (id, email, display_name, password_hash, role) VALUES ($1, $2, $3, $4, $5)', [userId, `eligibility-${uuidv7()}@example.com`, 'Eligibility Student', '$argon2id$placeholder', 'user']);
  await repository.pool.query('INSERT INTO user_settings (user_id, locale, daily_goal_target_xp, revision, timezone) VALUES ($1, $2, $3, $4, $5)', [userId, 'vi', 300, uuidv7(), 'Asia/Ho_Chi_Minh']);
  await repository.pool.query("UPDATE user_settings SET updated_at = '2020-01-01T00:00:00.000Z' WHERE user_id = $1", [userId]);
  const lessonId = '019f7e39-0003-7000-8000-000000000001';
  for (const acceptedAt of ['2026-03-06T12:00:00.000Z', '2026-03-07T12:00:00.000Z', '2026-03-08T12:00:00.000Z', '2026-03-10T12:00:00.000Z']) {
    await repository.pool.query('INSERT INTO learning_events (id, user_id, lesson_id, occurred_at, xp_earned, duration_seconds, event_type, idempotency_key, payload_hash, accepted_at) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)', [uuidv7(), userId, lessonId, acceptedAt, 10, 60, 'lesson.completed', uuidv7(), 'e'.repeat(64), acceptedAt]);
  }
  const service = new StreakRecoveryEligibilityService(repository, { now: () => new Date('2026-03-10T12:00:00.000Z') });
  assert.deepEqual(await service.get(userId), { eligible: true, missedLocalDate: '2026-03-09', policyVersion: 'streak-recovery-v1' });
  const unchanged = await repository.pool.query("SELECT (SELECT count(*)::int FROM streak_recoveries WHERE user_id = $1) AS recoveries, (SELECT count(*)::int FROM engagement_awards WHERE user_id = $1) AS awards, (SELECT count(*)::int FROM achievement_entitlements WHERE user_id = $1) AS entitlements", [userId]);
  assert.deepEqual(unchanged.rows[0], { recoveries: 0, awards: 0, entitlements: 0 });
});

async function recoveryEligibleUser() {
  const userId = uuidv7();
  await repository.pool.query('INSERT INTO users (id, email, display_name, password_hash, role) VALUES ($1, $2, $3, $4, $5)', [userId, `claim-${uuidv7()}@example.com`, 'Claim Student', '$argon2id$placeholder', 'user']);
  await repository.pool.query('INSERT INTO user_settings (user_id, locale, daily_goal_target_xp, revision, timezone) VALUES ($1, $2, $3, $4, $5)', [userId, 'vi', 300, uuidv7(), 'Asia/Ho_Chi_Minh']);
  await repository.pool.query("UPDATE user_settings SET updated_at = '2020-01-01T00:00:00.000Z' WHERE user_id = $1", [userId]);
  const lessonId = '019f7e39-0003-7000-8000-000000000001';
  for (const acceptedAt of ['2026-03-06T12:00:00.000Z', '2026-03-07T12:00:00.000Z', '2026-03-08T12:00:00.000Z', '2026-03-10T12:00:00.000Z']) {
    await repository.pool.query('INSERT INTO learning_events (id, user_id, lesson_id, occurred_at, xp_earned, duration_seconds, event_type, idempotency_key, payload_hash, accepted_at) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)', [uuidv7(), userId, lessonId, acceptedAt, 10, 60, 'lesson.completed', uuidv7(), 'f'.repeat(64), acceptedAt]);
  }
  return userId;
}

function claimService(engagementService = null) {
  const now = () => new Date('2026-03-10T12:00:00.000Z');
  return new StreakRecoveryClaimService(repository, engagementService ?? new EngagementService(repository, { now }), { now });
}

test('transactional recovery claim is idempotent, restores streak continuity, awards only a streak achievement, and creates no XP reward', async () => {
  const userId = await recoveryEligibleUser();
  const key = uuidv7();
  const service = claimService();
  const accepted = await service.claim({ userId, idempotencyKey: key });
  const replay = await service.claim({ userId, idempotencyKey: key });
  const { replayed: acceptedReplayed, ...acceptedData } = accepted;
  const { replayed: replayReplayed, ...replayData } = replay;
  assert.equal(acceptedReplayed, false); assert.equal(replayReplayed, true); assert.deepEqual(replayData, acceptedData);
  assert.equal(accepted.status, 'accepted'); assert.equal(accepted.missedLocalDate, '2026-03-09');
  assert.equal(await new EngagementService(repository, { now: () => new Date('2026-03-10T12:00:00.000Z') }).streak(userId), 5);
  const result = await repository.pool.query("SELECT (SELECT count(*)::int FROM streak_recoveries WHERE user_id = $1) AS recoveries, (SELECT count(*)::int FROM engagement_awards WHERE user_id = $1) AS awards, (SELECT count(*)::int FROM achievement_entitlements WHERE user_id = $1 AND achievement_key = 'streak_3_days') AS streak_awards, (SELECT count(*)::int FROM achievement_entitlements WHERE user_id = $1 AND achievement_key <> 'streak_3_days') AS other_awards", [userId]);
  assert.deepEqual(result.rows[0], { recoveries: 1, awards: 0, streak_awards: 1, other_awards: 0 });
});

test('concurrent recovery claims serialize per learner and preserve rolling, recovered-date, and adjacent-date protections', async () => {
  const userId = await recoveryEligibleUser();
  const service = claimService();
  const [left, right] = await Promise.all([service.claim({ userId, idempotencyKey: uuidv7() }), service.claim({ userId, idempotencyKey: uuidv7() })]);
  assert.equal([left, right].filter((result) => result.status === 'accepted').length, 1);
  assert.equal([left, right].filter((result) => result.status === 'ineligible' && result.reasonCode === 'rolling_limit_reached').length, 1);
  const stored = await repository.pool.query('SELECT count(*)::int AS count FROM streak_recoveries WHERE user_id = $1', [userId]);
  assert.equal(stored.rows[0].count, 1);
});

test('claim safely rejects an already recovered date, an adjacent recovered date, and an in-window rolling-cap record', async () => {
  const cases = [
    ['2026-03-09', '2026-01-01T00:00:00.000Z', 'missed_date_already_recovered'],
    ['2026-03-08', '2026-01-01T00:00:00.000Z', 'adjacent_recovered_date'],
    ['2026-02-01', '2026-03-01T12:00:00.000Z', 'rolling_limit_reached'],
  ];
  for (const [missedLocalDate, acceptedAt, reasonCode] of cases) {
    const userId = await recoveryEligibleUser();
    await repository.createStreakRecovery({
      userId, policyVersion: 'streak-recovery-v1', missedLocalDate, timezone: 'Asia/Ho_Chi_Minh',
      qualifyingActionType: 'learning_event', qualifyingActionId: uuidv7(), idempotencyKey: uuidv7(), payloadHash: 'd'.repeat(64), acceptedAt,
    });
    assert.deepEqual(await claimService().claim({ userId, idempotencyKey: uuidv7() }), { status: 'ineligible', reasonCode, policyVersion: 'streak-recovery-v1' });
    const records = await repository.pool.query('SELECT count(*)::int AS count FROM streak_recoveries WHERE user_id = $1', [userId]);
    assert.equal(records.rows[0].count, 1);
  }
});

test('claim transaction rolls back its recovery row when streak-achievement reconciliation fails', async () => {
  const userId = await recoveryEligibleUser();
  const service = claimService({ async reconcileStreakAchievements() { throw new Error('forced reconciliation failure'); } });
  await assert.rejects(service.claim({ userId, idempotencyKey: uuidv7() }), /forced reconciliation failure/u);
  const stored = await repository.pool.query('SELECT count(*)::int AS count FROM streak_recoveries WHERE user_id = $1', [userId]);
  assert.equal(stored.rows[0].count, 0);
});

test('reapplying the settings migration backfills canonical defaults for pre-existing identities', async () => {
  const userId = uuidv7();
  const revision = uuidv7();
  await repository.pool.query('INSERT INTO users (id, email, display_name, password_hash, role) VALUES ($1, $2, $3, $4, $5)', [userId, 'preexisting@example.com', 'Pre-existing', '$argon2id$placeholder', 'user']);
  await repository.pool.query('INSERT INTO user_profiles (user_id, revision) VALUES ($1, $2)', [userId, revision]);
  await applySchemaMigrations(repository.pool);
  const settings = await repository.findSettings(userId);
  assert.deepEqual({ locale: settings.locale, dailyGoalTargetXp: settings.dailyGoalTargetXp, revision: settings.revision }, { locale: 'vi', dailyGoalTargetXp: 300, revision });
});

test('registration, normalized duplicate detection, persisted Argon2id login, and repository recreation work', async () => {
  const email = 'normalised@example.com';
  const registered = await register(email);
  assert.equal(registered.response.status, 201);
  const duplicate = await register('NORMALISED@example.com');
  assert.equal(duplicate.response.status, 409);
  assert.equal(duplicate.payload.error.code, 'auth.email_already_registered');
  const row = await repository.pool.query('SELECT password_hash FROM users WHERE email = $1', [email]);
  assert.match(row.rows[0].password_hash, /^\$argon2id\$/u);
  const secondRepository = new PostgresIdentityRepository(config.databaseUrl);
  try {
    await secondRepository.verifyConnection();
    const persisted = await secondRepository.findUserById(registered.payload.data.user.id);
    assert.equal(persisted.email, email);
  } finally { await secondRepository.close(); }
  const login = await request('/api/v1/auth/login', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { email, password: 'Correct Horse Battery 1' } });
  assert.equal(login.response.status, 200);
});

test('rotation persists only a hash and reuse revokes the complete token family', async () => {
  const first = (await register()).payload.data;
  const stored = await repository.pool.query('SELECT refresh_token_hash FROM sessions');
  assert.equal(stored.rowCount, 1);
  assert.match(stored.rows[0].refresh_token_hash, /^[0-9a-f]{64}$/u);
  assert.notEqual(stored.rows[0].refresh_token_hash, first.refreshToken);
  const rotated = await request('/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { refreshToken: first.refreshToken } });
  assert.equal(rotated.response.status, 200);
  const reused = await request('/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { refreshToken: first.refreshToken } });
  assert.equal(reused.response.status, 401);
  assert.equal(reused.payload.error.code, 'auth.refresh_token_reused');
  const latest = await request('/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { refreshToken: rotated.payload.data.refreshToken } });
  assert.equal(latest.response.status, 401);
  assert.equal(latest.payload.error.code, 'auth.session_revoked');
});

test('two concurrent refreshes allow at most one rotation and finish with a revoked family', async () => {
  const session = (await register()).payload.data;
  const [left, right] = await Promise.all([
    request('/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { refreshToken: session.refreshToken } }),
    request('/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { refreshToken: session.refreshToken } }),
  ]);
  const results = [left, right];
  assert.equal(results.filter((result) => result.response.status === 200).length, 1);
  const loser = results.find((result) => result.response.status === 401);
  assert.equal(loser.payload.error.code, 'auth.refresh_token_reused');
  const winner = results.find((result) => result.response.status === 200);
  const family = await repository.pool.query('SELECT revoked_at, revoked_reason FROM refresh_token_families');
  assert.equal(family.rowCount, 1); assert.ok(family.rows[0].revoked_at); assert.equal(family.rows[0].revoked_reason, 'refresh_reuse');
  const latest = await request('/api/v1/auth/refresh', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { refreshToken: winner.payload.data.refreshToken } });
  assert.equal(latest.payload.error.code, 'auth.session_revoked');
});

test('logout current revokes only one family, logout all revokes every family, and protected access observes revocation', async () => {
  const first = (await register()).payload.data;
  const second = (await request('/api/v1/auth/login', { method: 'POST', headers: { 'X-Client-Platform': 'android' }, body: { email: first.user.email, password: 'Correct Horse Battery 1' } })).payload.data;
  const logout = await request('/api/v1/auth/logout', { method: 'POST', headers: { Authorization: `Bearer ${first.accessToken}`, 'X-Client-Platform': 'android' } });
  assert.equal(logout.response.status, 204);
  const firstDenied = await request('/api/v1/auth/me', { headers: { Authorization: `Bearer ${first.accessToken}` } });
  assert.equal(firstDenied.payload.error.code, 'auth.session_revoked');
  const secondAllowed = await request('/api/v1/auth/me', { headers: { Authorization: `Bearer ${second.accessToken}` } });
  assert.equal(secondAllowed.response.status, 200);
  const logoutAll = await request('/api/v1/auth/logout-all', { method: 'POST', headers: { Authorization: `Bearer ${second.accessToken}`, 'X-Client-Platform': 'android' } });
  assert.equal(logoutAll.response.status, 204);
  const secondDenied = await request('/api/v1/auth/me', { headers: { Authorization: `Bearer ${second.accessToken}` } });
  assert.equal(secondDenied.payload.error.code, 'auth.session_revoked');
});

test('profile revision is persisted and concurrent updates accept exactly one writer', async () => {
  const session = (await register()).payload.data;
  const headers = { Authorization: `Bearer ${session.accessToken}` };
  const profile = await request('/api/v1/me/profile', { headers });
  assert.equal(profile.response.headers.get('etag'), profile.payload.data.revision);
  const [left, right] = await Promise.all([
    request('/api/v1/me/profile', { method: 'PATCH', headers: { ...headers, 'If-Match': profile.payload.data.revision }, body: { avatarKey: 'left-avatar' } }),
    request('/api/v1/me/profile', { method: 'PATCH', headers: { ...headers, 'If-Match': profile.payload.data.revision }, body: { avatarKey: 'right-avatar' } }),
  ]);
  assert.equal([left, right].filter((result) => result.response.status === 200).length, 1);
  const conflict = [left, right].find((result) => result.response.status === 409);
  assert.equal(conflict.payload.error.code, 'conflict.revision_mismatch');
});

test('a failed identity transaction rolls back the user insert completely', async () => {
  const email = 'rollback@example.com';
  await assert.rejects(repository.createUserWithProfile({
    user: { id: uuidv7(), displayName: 'Rollback', email, passwordHash: '$argon2id$invalid', role: 'user', createdAt: new Date() },
    profile: { revision: null }, settings: { locale: 'vi', dailyGoalTargetXp: 300, revision: uuidv7() },
  }));
  assert.equal(await repository.findUserByEmail(email), null);
  const settings = await repository.pool.query('SELECT COUNT(*)::int AS count FROM user_settings');
  assert.equal(settings.rows[0].count, 0);
  const sessions = await repository.pool.query('SELECT COUNT(*)::int AS count FROM sessions');
  assert.equal(sessions.rows[0].count, 0);
});

test('shared settings persist through repository recreation and revision-controlled updates', async () => {
  const session = (await register()).payload.data;
  const headers = { Authorization: `Bearer ${session.accessToken}` };
  const current = await request('/api/v1/me/settings', { headers });
  assert.equal(current.response.status, 200);
  assert.equal(current.payload.data.locale, 'vi'); assert.equal(current.payload.data.dailyGoalTargetXp, 300);
  assert.equal(current.response.headers.get('etag'), current.payload.data.revision);
  const updated = await request('/api/v1/me/settings', { method: 'PUT', headers: { ...headers, 'If-Match': current.payload.data.revision }, body: { locale: 'en', dailyGoalTargetXp: 600 } });
  assert.equal(updated.response.status, 200);
  const secondRepository = new PostgresIdentityRepository(config.databaseUrl);
  try {
    const persisted = await secondRepository.findSettings(session.user.id);
    assert.equal(persisted.locale, 'en'); assert.equal(persisted.dailyGoalTargetXp, 600); assert.equal(persisted.revision, updated.payload.data.revision);
  } finally { await secondRepository.close(); }
});

test('shared settings concurrent stale writes are atomic and allow exactly one winner', async () => {
  const session = (await register()).payload.data;
  const headers = { Authorization: `Bearer ${session.accessToken}` };
  const current = await request('/api/v1/me/settings', { headers });
  const [left, right] = await Promise.all([
    request('/api/v1/me/settings', { method: 'PUT', headers: { ...headers, 'If-Match': current.payload.data.revision }, body: { locale: 'vi', dailyGoalTargetXp: 401 } }),
    request('/api/v1/me/settings', { method: 'PUT', headers: { ...headers, 'If-Match': current.payload.data.revision }, body: { locale: 'en', dailyGoalTargetXp: 402 } }),
  ]);
  assert.equal([left, right].filter((result) => result.response.status === 200).length, 1);
  assert.equal([left, right].find((result) => result.response.status === 409).payload.error.code, 'conflict.revision_mismatch');
});

test('seeded catalog and immutable learning events persist through repository recreation with a derived projection', async () => {
  const session = (await register()).payload.data;
  const headers = { Authorization: `Bearer ${session.accessToken}`, 'Idempotency-Key': uuidv7() };
  const subjects = await request('/api/v1/subjects', { headers: { Authorization: headers.Authorization } });
  assert.equal(subjects.response.status, 200); assert.equal(subjects.payload.data.length, 5);
  const lessonId = '019f7e39-0003-7000-8000-000000000001';
  const accepted = await request('/api/v1/learning-events', { method: 'POST', headers, body: { lessonId, occurredAt: new Date().toISOString(), durationSeconds: 180, eventType: 'lesson.completed' } });
  assert.equal(accepted.response.status, 201); assert.equal(accepted.payload.data.progress.totalXp, 10);
  const persisted = await repository.pool.query('SELECT id, user_id, lesson_id, xp_earned, duration_seconds, event_type, payload_hash FROM learning_events');
  assert.equal(persisted.rowCount, 1); assert.equal(persisted.rows[0].lesson_id, lessonId); assert.match(persisted.rows[0].payload_hash, /^[0-9a-f]{64}$/u);
  const secondRepository = new PostgresIdentityRepository(config.databaseUrl);
  try {
    const projection = await secondRepository.getProgressSource(session.user.id);
    assert.equal(projection.events.length, 1); assert.equal(projection.events[0].xpEarned, 10);
  } finally { await secondRepository.close(); }
});

test('campaign projection uses persisted catalog ordering and accepted completions without per-learner campaign rows', async () => {
  const session = (await register()).payload.data;
  const authorization = `Bearer ${session.accessToken}`;
  const before = await request('/api/v1/me/campaign', { headers: { Authorization: authorization } });
  assert.equal(before.response.status, 200);
  assert.equal(before.payload.data.campaignKey, 'core-learning-map');
  assert.equal(before.payload.data.zones.length, 5);
  assert.equal(before.payload.data.totalLessons, 25);
  assert.equal(before.payload.data.recommendedNodeId, '019f7e39-0003-7000-8000-000000000001');
  await request('/api/v1/learning-events', {
    method: 'POST',
    headers: { Authorization: authorization, 'Idempotency-Key': uuidv7() },
    body: { lessonId: '019f7e39-0003-7000-8000-000000000001', occurredAt: '2026-07-20T08:00:00.000Z', durationSeconds: 180, eventType: 'lesson.completed' },
  });
  const after = await request('/api/v1/me/campaign', { headers: { Authorization: authorization } });
  assert.equal(after.payload.data.completedLessons, 1);
  assert.equal(after.payload.data.recommendedNodeId, '019f7e39-0004-7000-8000-000000000001');
  assert.equal(after.payload.data.zones[0].topics[0].lessons[0].state, 'completed');
  const rows = await repository.pool.query("SELECT COUNT(*)::int AS count FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE 'campaign_%'");
  assert.equal(rows.rows[0].count, 0);
  const source = await repository.getCampaignSource(session.user.id);
  const direct = await new CampaignService({ getCampaignSource: async () => source }).get(session.user.id);
  assert.deepEqual(direct.recommendedNodeId, after.payload.data.recommendedNodeId);
});

test('lesson completions persist through repository recreation, survive a restart, and never gain a duplicate from a repeat completion', async () => {
  const session = (await register()).payload.data;
  const authorization = `Bearer ${session.accessToken}`;
  const lessonId = '019f7e39-0003-7000-8000-000000000001';

  const empty = await request('/api/v1/me/lesson-completions', { headers: { Authorization: authorization } });
  assert.equal(empty.response.status, 200); assert.deepEqual(empty.payload.data, []);

  const first = await request('/api/v1/learning-events', {
    method: 'POST',
    headers: { Authorization: authorization, 'Idempotency-Key': uuidv7() },
    body: { lessonId, occurredAt: '2026-07-20T08:00:00.000Z', durationSeconds: 180, eventType: 'lesson.completed' },
  });
  assert.equal(first.response.status, 201);

  // A second, later completion of the same lesson must not move completedAt.
  const second = await request('/api/v1/learning-events', {
    method: 'POST',
    headers: { Authorization: authorization, 'Idempotency-Key': uuidv7() },
    body: { lessonId, occurredAt: '2026-07-22T08:00:00.000Z', durationSeconds: 90, eventType: 'lesson.completed' },
  });
  assert.equal(second.response.status, 201);

  const afterFirstProcess = await request('/api/v1/me/lesson-completions', { headers: { Authorization: authorization } });
  assert.deepEqual(afterFirstProcess.payload.data, [{ lessonId, completedAt: '2026-07-20T08:00:00.000Z' }]);

  // A fresh repository instance simulates the backend restarting: nothing in
  // this read model may live only in this process's memory.
  const secondRepository = new PostgresIdentityRepository(config.databaseUrl);
  try {
    const events = await secondRepository.listCompletionEvents(session.user.id);
    assert.equal(events.length, 2);
  } finally { await secondRepository.close(); }

  const stillThere = await request('/api/v1/me/lesson-completions', { headers: { Authorization: authorization } });
  assert.deepEqual(stillThere.payload.data, [{ lessonId, completedAt: '2026-07-20T08:00:00.000Z' }]);
});

test('seeded quizzes and immutable quiz attempts persist through repository recreation without learning progress side effects', async () => {
  const session = (await register()).payload.data;
  const authorization = `Bearer ${session.accessToken}`;
  const quizzes = await request('/api/v1/quizzes?lessonId=019f7e39-0003-7000-8000-000000000001', { headers: { Authorization: authorization } });
  assert.equal(quizzes.response.status, 200);
  assert.equal(quizzes.payload.data[0].id, '019f7e39-0006-7000-8000-000000000001');
  assert.equal(quizzes.payload.data[0].questionCount, 15);
  const detail = await request(`/api/v1/quizzes/${quizzes.payload.data[0].id}`, { headers: { Authorization: authorization } });
  assert.equal(detail.response.status, 200);
  assert.equal(detail.payload.data.questions.length, 15);
  assert.equal(detail.payload.data.questions.every((question) => question.options.length === 4), true);
  assert.equal(JSON.stringify(detail.payload.data).includes('correct'), false);
  const attempt = await request('/api/v1/quiz-attempts', {
    method: 'POST',
    headers: { Authorization: authorization, 'Idempotency-Key': uuidv7() },
    body: {
      quizId: '019f7e39-0006-7000-8000-000000000001',
      answers: detail.payload.data.questions.map((question) => ({ questionId: question.id, selectedOptionId: question.options[0].id })),
    },
  });
  assert.equal(attempt.response.status, 201);
  assert.equal(attempt.payload.data.correctAnswers, 15);
  assert.equal(attempt.payload.data.scorePercentage, 100);
  const persisted = await repository.pool.query('SELECT total_questions, correct_answers, score_percentage::float AS score, payload_hash FROM quiz_attempts');
  assert.equal(persisted.rows[0].total_questions, 15);
  assert.equal(persisted.rows[0].correct_answers, 15);
  assert.equal(persisted.rows[0].score, 100);
  assert.match(persisted.rows[0].payload_hash, /^[0-9a-f]{64}$/u);
  const events = await repository.pool.query('SELECT COUNT(*)::int AS count FROM learning_events');
  assert.equal(events.rows[0].count, 0);
  const secondRepository = new PostgresIdentityRepository(config.databaseUrl);
  try {
    const detail = await secondRepository.findQuizDetail('019f7e39-0006-7000-8000-000000000001');
    assert.equal(detail.questions[0].options[0].correct, undefined);
  } finally { await secondRepository.close(); }
});

test('Full Product v1 wrong-answer, boss reward, wallet, shop, and inventory flow persists atomically', async () => {
  const session = (await register()).payload.data;
  const authorization = `Bearer ${session.accessToken}`;
  const quizId = '019f7e39-0006-7000-8000-000000000001';
  const quizQuestionId = '019f7e39-0007-7000-8000-000000000001';
  const knownWrongOptionId = '019f7e39-0010-7000-8000-000000000001';
  const bossChallengeId = '019f7e39-0200-7000-8000-000000000001';
  const shopItemId = '019f7e39-0210-7000-8000-000000000001';

  const quiz = await request(`/api/v1/quizzes/${quizId}`, {
    headers: { Authorization: authorization },
  });
  const quizAttempt = await request('/api/v1/quiz-attempts', {
    method: 'POST',
    headers: { Authorization: authorization, 'Idempotency-Key': uuidv7() },
    body: {
      quizId,
      answers: quiz.payload.data.questions.map((question) => ({
        questionId: question.id,
        selectedOptionId: question.id === quizQuestionId
          ? knownWrongOptionId
          : question.options[0].id,
      })),
    },
  });
  assert.equal(quizAttempt.response.status, 201);

  const wrongAnswers = await request('/api/v1/me/wrong-answers?page=1&pageSize=20', {
    headers: { Authorization: authorization },
  });
  assert.equal(wrongAnswers.response.status, 200);
  assert.equal(wrongAnswers.payload.data.totalItems, 1);
  assert.equal(wrongAnswers.payload.data.items[0].questionId, quizQuestionId);
  assert.equal(wrongAnswers.payload.data.items[0].correctOptionText, 'Good morning');

  const boss = await request('/api/v1/me/boss-challenge', {
    headers: { Authorization: authorization },
  });
  assert.equal(boss.response.status, 200);
  assert.equal(boss.payload.data.id, bossChallengeId);
  assert.doesNotMatch(JSON.stringify(boss.payload.data), /"correct"/u);

  const bossKey = uuidv7();
  const bossBody = {
    challengeId: bossChallengeId,
    answers: [
      {
        questionId: '019f7e39-0201-7000-8000-000000000001',
        selectedOptionId: '019f7e39-0203-7000-8000-000000000001',
      },
      {
        questionId: '019f7e39-0202-7000-8000-000000000001',
        selectedOptionId: '019f7e39-0206-7000-8000-000000000001',
      },
    ],
  };
  const bossAttempt = await request('/api/v1/boss-attempts', {
    method: 'POST',
    headers: { Authorization: authorization, 'Idempotency-Key': bossKey },
    body: bossBody,
  });
  assert.equal(bossAttempt.response.status, 201);
  assert.equal(bossAttempt.payload.data.rewardShells, 25);
  assert.equal(bossAttempt.payload.data.walletBalance, 25);
  const bossReplay = await request('/api/v1/boss-attempts', {
    method: 'POST',
    headers: { Authorization: authorization, 'Idempotency-Key': bossKey },
    body: bossBody,
  });
  assert.equal(bossReplay.response.status, 200);
  assert.equal(bossReplay.payload.data.attemptId, bossAttempt.payload.data.attemptId);
  assert.equal(bossReplay.payload.data.walletBalance, 25);

  const purchaseKey = uuidv7();
  const purchaseBody = { itemId: shopItemId };
  const purchase = await request('/api/v1/me/shop-purchases', {
    method: 'POST',
    headers: { Authorization: authorization, 'Idempotency-Key': purchaseKey },
    body: purchaseBody,
  });
  assert.equal(purchase.response.status, 201);
  assert.equal(purchase.payload.data.balance, 5);
  const purchaseReplay = await request('/api/v1/me/shop-purchases', {
    method: 'POST',
    headers: { Authorization: authorization, 'Idempotency-Key': purchaseKey },
    body: purchaseBody,
  });
  assert.equal(purchaseReplay.response.status, 200);
  assert.equal(purchaseReplay.payload.data.purchaseId, purchase.payload.data.purchaseId);

  const economy = await request('/api/v1/me/economy', {
    headers: { Authorization: authorization },
  });
  assert.equal(economy.payload.data.balance, 5);
  assert.equal(economy.payload.data.shopItems.find((item) => item.id === shopItemId).owned, true);
  assert.equal(economy.payload.data.inventory.find((item) => item.itemId === shopItemId).quantity, 1);

  const persisted = await repository.pool.query(`
    SELECT
      (SELECT COUNT(*)::int FROM boss_attempts WHERE user_id = $1) AS boss_attempts,
      (SELECT COUNT(*)::int FROM wallet_transactions WHERE user_id = $1 AND transaction_type = 'boss_reward') AS boss_rewards,
      (SELECT COUNT(*)::int FROM shop_purchases WHERE user_id = $1) AS purchases,
      (SELECT quantity FROM learner_inventory WHERE user_id = $1 AND item_id = $2) AS quantity
  `, [session.user.id, shopItemId]);
  assert.deepEqual(persisted.rows[0], {
    boss_attempts: 1,
    boss_rewards: 1,
    purchases: 1,
    quantity: 1,
  });
});

test('a graceful backend shutdown closes its PostgreSQL connection pool cleanly', async () => {
  const disposable = new PostgresIdentityRepository(config.databaseUrl);
  const backend = await startBackend({ config: { ...config, port: 0 }, repository: disposable, logger });
  await backend.shutdown('integration-test');
  await assert.rejects(disposable.pool.query('SELECT 1'));
});
