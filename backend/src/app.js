import express from 'express';
import cors from 'cors';
import { v7 as uuidv7 } from 'uuid';
import { ApiError, validationError } from './errors.js';
import { createLogger, errorHandler, requestContext, respond } from './http.js';
import { verifyAccessToken } from './tokens.js';
import { isLoopbackRequest } from './config.js';
import { bossAttemptRequest, clientPlatform, deckIdQuery, emptyRequestBody, flashcardReviewRequest, idempotencyKey, learningEventRequest, lessonIdQuery, loginRequest, purchaseRequest, quizAttemptRequest, registerRequest, replaceSettingsRequest, tutorRequest, updateProfileRequest, uuidPath, wrongAnswerQuery } from './validation.js';
import { AuthService } from './services/auth-service.js';
import { LearningService } from './services/learning-service.js';
import { QuizService } from './services/quiz-service.js';
import { FlashcardService } from './services/flashcard-service.js';
import { SettingsService } from './services/settings-service.js';
import { AiTutorService } from './services/ai-tutor-service.js';
import { EngagementService } from './services/engagement-service.js';
import { StreakRecoveryEligibilityService } from './services/streak-recovery-eligibility-service.js';
import { StreakRecoveryClaimService } from './services/streak-recovery-claim-service.js';
import { CampaignService } from './services/campaign-service.js';
import { BossChallengeService, EconomyService, WrongAnswerService } from './services/full-product-service.js';
import { createRateAdmission } from './rate-admission.js';
import { createFakeTutorProvider } from './fake-tutor-provider.js';
import { createRateLimiters } from './rate-limit.js';

const REFRESH_COOKIE = 'asm_refresh';

function parseCookie(header, name) {
  if (!header) return undefined;
  for (const part of header.split(';')) {
    const [key, ...value] = part.trim().split('=');
    if (key === name) return decodeURIComponent(value.join('='));
  }
  return undefined;
}

function normalizedOrigin(value) {
  if (!value) return undefined;
  try {
    const origin = new URL(value).origin;
    return origin === value ? origin : undefined;
  } catch { return undefined; }
}

function assertAllowedBrowserOrigin(req, config, platform) {
  if (platform !== 'web') return;
  const origin = normalizedOrigin(req.get('Origin'));
  if (!origin || !config.webOrigins.includes(origin)) throw new ApiError(403, 'server.internal', 'Origin is not allowed.');
}

function cookieOptions(req, config) {
  if (!config.refreshCookieSecure && !isLoopbackRequest(req)) throw new ApiError(403, 'server.internal', 'Origin is not allowed.');
  return {
    httpOnly: true,
    secure: config.refreshCookieSecure,
    sameSite: 'lax',
    path: '/api/v1/auth',
    ...(config.refreshCookieDomain ? { domain: config.refreshCookieDomain } : {}),
  };
}

function refreshCookie(req, res, token, config) {
  res.cookie(REFRESH_COOKIE, token, { ...cookieOptions(req, config), maxAge: config.refreshTokenTtlSeconds * 1000 });
}

function clearRefreshCookie(req, res, config) {
  res.clearCookie(REFRESH_COOKIE, cookieOptions(req, config));
}

function sessionData(session, platform) {
  if (platform === 'android') return session;
  const { refreshToken: _refreshToken, refreshTokenExpiresAt: _refreshTokenExpiresAt, ...webSession } = session;
  return webSession;
}

function authRequired(repository, config) {
  return async (req, _res, next) => {
    try {
      const authorization = req.get('Authorization');
      if (!authorization?.startsWith('Bearer ')) throw new ApiError(401, 'auth.session_expired', 'A valid bearer access token is required.');
      const claims = verifyAccessToken(authorization.slice(7), config);
      if (typeof claims.sub !== 'string' || typeof claims.fid !== 'string' || typeof claims.role !== 'string') throw new ApiError(401, 'auth.session_expired', 'The access token is invalid.');
      if (!(await repository.isFamilyActive(claims.sub, claims.fid))) throw new ApiError(401, 'auth.session_revoked', 'The session has been revoked.');
      req.auth = { userId: claims.sub, familyId: claims.fid, role: claims.role };
      next();
    } catch (error) {
      if (error instanceof ApiError) return next(error);
      return next(new ApiError(401, 'auth.session_expired', 'The access token is invalid or expired.'));
    }
  };
}

export function createApp({ config, repository, logger = createLogger(), enableRateLimit = true, rateAdmission, fakeProvider, engagementService: suppliedEngagementService, streakRecoveryEligibilityService: suppliedStreakRecoveryEligibilityService, streakRecoveryClaimService: suppliedStreakRecoveryClaimService, campaignService: suppliedCampaignService } = {}) {
  const app = express();
  const service = new AuthService(repository, config);
  const settingsService = new SettingsService(repository);
  const learningService = new LearningService(repository, { futureToleranceSeconds: config.learningEventFutureToleranceSeconds });
  const quizService = new QuizService(repository);
  const flashcardService = new FlashcardService(repository, { futureToleranceSeconds: config.learningEventFutureToleranceSeconds });
  const engagementService = suppliedEngagementService ?? new EngagementService(repository);
  const streakRecoveryEligibilityService = suppliedStreakRecoveryEligibilityService ?? new StreakRecoveryEligibilityService(repository);
  const streakRecoveryClaimService = suppliedStreakRecoveryClaimService ?? new StreakRecoveryClaimService(repository, engagementService);
  const campaignService = suppliedCampaignService ?? new CampaignService(repository);
  const wrongAnswerService = new WrongAnswerService(repository);
  const bossChallengeService = new BossChallengeService(repository);
  const economyService = new EconomyService(repository);
  const effectiveRateAdmission = rateAdmission ?? createRateAdmission({
    burstLimit: config.aiBurstLimit ?? 5,
    burstWindowMs: (config.aiBurstWindowSeconds ?? 60) * 1000,
    rollingLimit: config.aiRollingLimit ?? 20,
    rollingWindowMs: (config.aiRollingWindowSeconds ?? 60) * 1000,
  });
  const effectiveFakeProvider = fakeProvider ?? createFakeTutorProvider({ defaultTimeoutMs: config.aiProviderTimeoutMs ?? 15000 });
  const aiTutorService = new AiTutorService({ repository, rateAdmission: effectiveRateAdmission, fakeProvider: effectiveFakeProvider, config });
  const limiters = createRateLimiters(config.environment);

  app.disable('x-powered-by');
  app.use(requestContext(logger));
  app.use(cors({
    origin(origin, callback) {
      if (!origin || config.webOrigins.includes(normalizedOrigin(origin))) return callback(null, true);
      return callback(new ApiError(403, 'server.internal', 'Origin is not allowed.'));
    },
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'OPTIONS'],
    allowedHeaders: ['Authorization', 'Content-Type', 'If-Match', 'Idempotency-Key', 'X-Client-Platform', 'X-Request-Id'],
    exposedHeaders: ['ETag', 'X-Request-Id'],
  }));
  app.use(express.json({ limit: '32kb', strict: true }));
  app.use((req, res, next) => {
    res.set({ 'X-Content-Type-Options': 'nosniff', 'X-Frame-Options': 'DENY', 'Referrer-Policy': 'strict-origin-when-cross-origin', 'Permissions-Policy': 'camera=(), microphone=(), geolocation=()' });
    next();
  });
  if (enableRateLimit) app.use(limiters.global);

  app.get('/health', (_req, res) => res.status(200).json({ data: { status: 'ok' }, meta: { requestId: _req.requestId } }));

  app.post('/api/v1/auth/register', enableRateLimit ? limiters.credentials : (_req, _res, next) => next(), async (req, res, next) => {
    try {
      const platform = clientPlatform(req);
      const session = await service.register(registerRequest(req.body), platform);
      if (platform === 'web') refreshCookie(req, res, session.refreshToken, config);
      res.status(201).json({ data: sessionData(session, platform), meta: { requestId: req.requestId } });
    } catch (error) { next(error); }
  });

  app.post('/api/v1/auth/login', enableRateLimit ? limiters.credentials : (_req, _res, next) => next(), async (req, res, next) => {
    try {
      const platform = clientPlatform(req);
      const session = await service.login(loginRequest(req.body), platform);
      if (platform === 'web') refreshCookie(req, res, session.refreshToken, config);
      res.status(200).json({ data: sessionData(session, platform), meta: { requestId: req.requestId } });
    } catch (error) { next(error); }
  });

  app.post('/api/v1/auth/refresh', enableRateLimit ? limiters.refresh : (_req, _res, next) => next(), async (req, res, next) => {
    try {
      const platform = clientPlatform(req);
      let refreshToken;
      if (platform === 'web') {
        assertAllowedBrowserOrigin(req, config, platform);
        if (req.body && Object.keys(req.body).length) throw validationError([{ field: 'body', reason: 'must_be_omitted_for_web' }]);
        refreshToken = parseCookie(req.get('Cookie'), REFRESH_COOKIE);
      } else {
        if (!req.body || Object.keys(req.body).length !== 1 || typeof req.body.refreshToken !== 'string' || !req.body.refreshToken) throw validationError([{ field: 'refreshToken', reason: 'required_for_android' }]);
        refreshToken = req.body.refreshToken;
      }
      if (!refreshToken) throw new ApiError(401, 'auth.refresh_token_invalid', 'The refresh token is invalid.');
      const session = await service.refresh(refreshToken);
      if (platform === 'web') refreshCookie(req, res, session.refreshToken, config);
      respond(res, sessionData(session, platform));
    } catch (error) { next(error); }
  });

  app.post('/api/v1/auth/logout', authRequired(repository, config), async (req, res, next) => {
    try { const platform = clientPlatform(req); assertAllowedBrowserOrigin(req, config, platform); await repository.revokeFamily(req.auth.userId, req.auth.familyId); if (platform === 'web') clearRefreshCookie(req, res, config); res.status(204).end(); } catch (error) { next(error); }
  });
  app.post('/api/v1/auth/logout-all', authRequired(repository, config), async (req, res, next) => {
    try { const platform = clientPlatform(req); assertAllowedBrowserOrigin(req, config, platform); await repository.revokeAllFamilies(req.auth.userId); if (platform === 'web') clearRefreshCookie(req, res, config); res.status(204).end(); } catch (error) { next(error); }
  });
  app.get('/api/v1/auth/me', authRequired(repository, config), async (req, res, next) => {
    try {
      const user = await repository.findUserById(req.auth.userId);
      if (!user) throw new ApiError(401, 'auth.session_revoked', 'The session has been revoked.');
      respond(res, { id: user.id, displayName: user.displayName, email: user.email, createdAt: new Date(user.createdAt).toISOString() });
    } catch (error) { next(error); }
  });
  app.get('/api/v1/me/profile', authRequired(repository, config), async (req, res, next) => {
    try {
      const profile = await repository.findProfile(req.auth.userId);
      if (!profile) throw new ApiError(401, 'auth.session_revoked', 'The session has been revoked.');
      res.setHeader('ETag', profile.revision);
      respond(res, profile);
    } catch (error) { next(error); }
  });
  app.patch('/api/v1/me/profile', authRequired(repository, config), async (req, res, next) => {
    try {
      const revision = req.get('If-Match');
      if (!revision) throw validationError([{ field: 'If-Match', reason: 'required' }]);
      const result = await repository.updateProfile({ userId: req.auth.userId, revision, patch: updateProfileRequest(req.body), nextRevision: uuidv7() });
      if (result.status === 'conflict') throw new ApiError(409, 'conflict.revision_mismatch', 'The profile has changed. Read it again before retrying.');
      res.setHeader('ETag', result.profile.revision);
      respond(res, result.profile);
    } catch (error) { next(error); }
  });
  app.get('/api/v1/me/settings', authRequired(repository, config), async (req, res, next) => {
    try {
      const settings = await settingsService.get(req.auth.userId);
      res.setHeader('ETag', settings.revision);
      respond(res, settings);
    } catch (error) { next(error); }
  });
  app.put('/api/v1/me/settings', authRequired(repository, config), async (req, res, next) => {
    try {
      const revision = req.get('If-Match');
      if (!revision) throw validationError([{ field: 'If-Match', reason: 'required' }]);
      const settings = await settingsService.replace({ userId: req.auth.userId, revision, settings: replaceSettingsRequest(req.body) });
      res.setHeader('ETag', settings.revision);
      respond(res, settings);
    } catch (error) { next(error); }
  });

  app.get('/api/v1/subjects', authRequired(repository, config), async (req, res, next) => {
    try { respond(res, await learningService.listSubjects()); } catch (error) { next(error); }
  });
  app.get('/api/v1/subjects/:subjectId', authRequired(repository, config), async (req, res, next) => {
    try { respond(res, await learningService.getSubject(req.params.subjectId)); } catch (error) { next(error); }
  });
  app.get('/api/v1/subjects/:subjectId/topics', authRequired(repository, config), async (req, res, next) => {
    try { respond(res, await learningService.listTopics(req.params.subjectId)); } catch (error) { next(error); }
  });
  app.get('/api/v1/topics/:topicId', authRequired(repository, config), async (req, res, next) => {
    try { respond(res, await learningService.getTopic(req.params.topicId)); } catch (error) { next(error); }
  });
  app.get('/api/v1/topics/:topicId/lessons', authRequired(repository, config), async (req, res, next) => {
    try { respond(res, await learningService.listLessons(req.params.topicId)); } catch (error) { next(error); }
  });
  app.get('/api/v1/lessons/:lessonId', authRequired(repository, config), async (req, res, next) => {
    try { respond(res, await learningService.getLesson(req.params.lessonId)); } catch (error) { next(error); }
  });
  app.post('/api/v1/learning-events', authRequired(repository, config), async (req, res, next) => {
    try {
      const result = await learningService.appendEvent({
        userId: req.auth.userId,
        idempotencyKey: idempotencyKey(req),
        input: learningEventRequest(req.body, new Date(), config.learningEventFutureToleranceSeconds),
      });
      if (repository.pool) await engagementService.reconcile(req.auth.userId);
      if (result.status === 'replayed') res.setHeader('X-Idempotent-Replay', 'true');
      res.status(result.status === 'created' ? 201 : 200).json({ data: { event: result.event, progress: result.progress }, meta: { requestId: req.requestId } });
    } catch (error) { next(error); }
  });
  app.get('/api/v1/me/progress', authRequired(repository, config), async (req, res, next) => {
    try { respond(res, await learningService.getProgress(req.auth.userId)); } catch (error) { next(error); }
  });
  app.get('/api/v1/me/engagement', authRequired(repository, config), async (req, res, next) => {
    try { respond(res, await engagementService.get(req.auth.userId)); } catch (error) { next(error); }
  });
  app.get('/api/v1/me/campaign', authRequired(repository, config), async (req, res, next) => {
    try { respond(res, await campaignService.get(req.auth.userId)); } catch (error) { next(error); }
  });
  app.get('/api/v1/me/streak-recovery', authRequired(repository, config), async (req, res, next) => {
    try {
      const [eligibility, streak] = await Promise.all([
        streakRecoveryEligibilityService.get(req.auth.userId),
        engagementService.streak(req.auth.userId),
      ]);
      respond(res, { ...eligibility, streak });
    } catch (error) { next(error); }
  });
  app.post('/api/v1/me/streak-recoveries', authRequired(repository, config), async (req, res, next) => {
    try {
      emptyRequestBody(req.body);
      const result = await streakRecoveryClaimService.claim({ userId: req.auth.userId, idempotencyKey: idempotencyKey(req) });
      if (result.status === 'ineligible') {
        throw new ApiError(409, 'streak_recovery.ineligible', 'Streak recovery is not available.', [{ field: 'recovery', reason: result.reasonCode }]);
      }
      const { replayed, ...data } = result;
      if (replayed) res.setHeader('X-Idempotent-Replay', 'true');
      res.status(replayed ? 200 : 201).json({ data, meta: { requestId: req.requestId } });
    } catch (error) { next(error); }
  });
  app.get('/api/v1/me/lesson-completions', authRequired(repository, config), async (req, res, next) => {
    try { respond(res, await learningService.listCompletions(req.auth.userId)); } catch (error) { next(error); }
  });
  app.get('/api/v1/quizzes', authRequired(repository, config), async (req, res, next) => {
    try { respond(res, await quizService.listQuizzes(lessonIdQuery(req.query))); } catch (error) { next(error); }
  });
  app.get('/api/v1/quizzes/:quizId', authRequired(repository, config), async (req, res, next) => {
    try { respond(res, await quizService.getQuiz(uuidPath(req.params.quizId, 'quizId'))); } catch (error) { next(error); }
  });
  app.post('/api/v1/quiz-attempts', authRequired(repository, config), async (req, res, next) => {
    try {
      const result = await quizService.submitAttempt({
        userId: req.auth.userId,
        idempotencyKey: idempotencyKey(req),
        input: quizAttemptRequest(req.body),
      });
      if (repository.pool) await engagementService.reconcile(req.auth.userId);
      if (result.status === 'replayed') res.setHeader('X-Idempotent-Replay', 'true');
      res.status(result.status === 'created' ? 201 : 200).json({ data: result.result, meta: { requestId: req.requestId } });
    } catch (error) { next(error); }
  });

  app.get('/api/v1/me/wrong-answers', authRequired(repository, config), async (req, res, next) => {
    try {
      respond(res, await wrongAnswerService.list({ userId: req.auth.userId, ...wrongAnswerQuery(req.query) }));
    } catch (error) { next(error); }
  });

  app.get('/api/v1/me/boss-challenge', authRequired(repository, config), async (req, res, next) => {
    try { respond(res, await bossChallengeService.getActive(req.auth.userId)); } catch (error) { next(error); }
  });

  app.post('/api/v1/boss-attempts', authRequired(repository, config), async (req, res, next) => {
    try {
      const result = await bossChallengeService.submit({
        userId: req.auth.userId,
        idempotencyKey: idempotencyKey(req),
        input: bossAttemptRequest(req.body),
      });
      if (result.status === 'conflict') throw new ApiError(409, 'boss.idempotency_key_reused', 'The idempotency key was reused with different boss attempt data.');
      if (result.status === 'replayed') res.setHeader('X-Idempotent-Replay', 'true');
      res.status(result.status === 'created' ? 201 : 200).json({ data: result.result, meta: { requestId: req.requestId } });
    } catch (error) { next(error); }
  });

  app.get('/api/v1/me/economy', authRequired(repository, config), async (req, res, next) => {
    try { respond(res, await economyService.get(req.auth.userId)); } catch (error) { next(error); }
  });

  app.post('/api/v1/me/shop-purchases', authRequired(repository, config), async (req, res, next) => {
    try {
      const result = await economyService.purchase({
        userId: req.auth.userId,
        idempotencyKey: idempotencyKey(req),
        itemId: purchaseRequest(req.body).itemId,
      });
      if (result.status === 'replayed') res.setHeader('X-Idempotent-Replay', 'true');
      res.status(result.status === 'created' ? 201 : 200).json({ data: result.result, meta: { requestId: req.requestId } });
    } catch (error) { next(error); }
  });

  app.post('/api/v1/ai/tutor-responses', authRequired(repository, config), async (req, res, next) => {
    try {
      const { lessonId, message } = tutorRequest(req.body);
      const result = await aiTutorService.generateTutorResponse({
        userId: req.auth.userId,
        idempotencyKey: idempotencyKey(req),
        lessonId,
        message,
      });
      if (!result.created) res.setHeader('X-Idempotent-Replay', 'true');
      res.status(result.created ? 201 : 200).json({ data: result.response, meta: { requestId: req.requestId } });
    } catch (error) { next(error); }
  });

  // Flashcards ---------------------------------------------------------------
  // Scheduling is server-owned: clients send only cardId, outcome and
  // reviewedAt. Reviews earn no XP in v1 and touch no progress projection.

  app.get('/api/v1/flashcard-decks', authRequired(repository, config), async (req, res, next) => {
    try {
      // lessonIdQuery returns the id itself, not a wrapper object.
      respond(res, await flashcardService.listDecks(lessonIdQuery(req.query)));
    } catch (error) { next(error); }
  });

  app.get('/api/v1/flashcard-decks/:deckId', authRequired(repository, config), async (req, res, next) => {
    try { respond(res, await flashcardService.getDeck(uuidPath(req.params.deckId, 'deckId'))); } catch (error) { next(error); }
  });

  app.get('/api/v1/me/flashcard-queue', authRequired(repository, config), async (req, res, next) => {
    try {
      const { deckId, dueOnly, limit } = deckIdQuery(req.query);
      respond(res, await flashcardService.getQueue({ userId: req.auth.userId, deckId, dueOnly, limit }));
    } catch (error) { next(error); }
  });

  app.post('/api/v1/flashcard-reviews', authRequired(repository, config), async (req, res, next) => {
    try {
      const result = await flashcardService.submitReview({
        userId: req.auth.userId,
        idempotencyKey: idempotencyKey(req),
        input: flashcardReviewRequest(req.body, new Date(), config.learningEventFutureToleranceSeconds),
      });
      if (repository.pool) await engagementService.reconcile(req.auth.userId);
      const created = result.status === 'created';
      if (!created) res.setHeader('X-Idempotent-Replay', 'true');
      res.status(created ? 201 : 200).json({
        data: { reviewId: result.review.id, state: result.state, acceptedAt: result.review.acceptedAt },
        meta: { requestId: req.requestId },
      });
    } catch (error) { next(error); }
  });

  app.post('/api/v1/me/flashcard-decks/:deckId/reset', authRequired(repository, config), async (req, res, next) => {
    try {
      // Idempotency-Key is required by the contract; resetting is naturally
      // idempotent, so the key is validated but needs no replay store.
      idempotencyKey(req);
      const result = await flashcardService.resetDeck({
        userId: req.auth.userId,
        deckId: uuidPath(req.params.deckId, 'deckId'),
      });
      res.status(201).json({ data: result, meta: { requestId: req.requestId } });
    } catch (error) { next(error); }
  });

  app.use((req, _res, next) => next(new ApiError(404, 'server.internal', `Route not found: ${req.method} ${req.path}`)));
  app.use(errorHandler(logger));
  return app;
}
