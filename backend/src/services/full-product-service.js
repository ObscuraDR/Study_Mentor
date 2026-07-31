import { createHash } from 'node:crypto';
import { v7 as uuidv7 } from 'uuid';
import { ApiError } from '../errors.js';

function payloadHash(value) {
  return createHash('sha256').update(JSON.stringify(value)).digest('hex');
}

export class WrongAnswerService {
  constructor(repository) {
    this.repository = repository;
  }

  async list({ userId, page, pageSize, lessonId, quizId }) {
    return this.repository.listWrongAnswers({ userId, page, pageSize, lessonId, quizId });
  }
}

export class BossChallengeService {
  constructor(repository) {
    this.repository = repository;
  }

  async getActive(userId) {
    const challenge = await this.repository.findActiveBossChallenge(userId);
    if (!challenge) throw new ApiError(404, 'boss.not_found', 'No active boss challenge is available.');
    return challenge;
  }

  async submit({ userId, idempotencyKey, input }) {
    const source = await this.repository.findBossChallengeSource(input.challengeId);
    if (!source) throw new ApiError(404, 'boss.not_found', 'The boss challenge was not found.');
    if (!source.available) throw new ApiError(409, 'boss.unavailable', 'The boss challenge is not available.');
    const submitted = new Map();
    for (const answer of input.answers) {
      if (submitted.has(answer.questionId)) throw new ApiError(422, 'boss.duplicate_answer', 'Each boss question may be answered only once.');
      submitted.set(answer.questionId, answer.selectedOptionId);
    }
    if (submitted.size !== source.questions.length) throw new ApiError(422, 'boss.incomplete_attempt', 'All boss questions must be answered.');
    let correctAnswers = 0;
    const answers = source.questions.map((question) => {
      const selectedOptionId = submitted.get(question.id);
      const option = question.options.find((candidate) => candidate.id === selectedOptionId);
      if (!option) throw new ApiError(422, 'boss.option_not_found', 'An answer option does not belong to its question.');
      const correctOption = question.options.find((candidate) => candidate.correct);
      if (!correctOption) throw new ApiError(422, 'boss.invalid_challenge', 'A boss question is not objectively scorable.');
      const correct = option.id === correctOption.id;
      if (correct) correctAnswers += 1;
      return { questionId: question.id, selectedOptionId: option.id, correct };
    });
    const totalQuestions = source.questions.length;
    const scorePercentage = Number(((correctAnswers / totalQuestions) * 100).toFixed(2));
    const passed = scorePercentage >= source.passingPercentage;
    return this.repository.createBossAttempt({
      attempt: {
        id: uuidv7(),
        userId,
        challengeId: source.id,
        submittedAt: new Date().toISOString(),
        totalQuestions,
        correctAnswers,
        scorePercentage,
        passed,
        idempotencyKey,
      },
      answers,
      rewardShells: passed ? source.rewardShells : 0,
      payloadHash: payloadHash(input),
    });
  }
}

export class EconomyService {
  constructor(repository) {
    this.repository = repository;
  }

  async get(userId) {
    return this.repository.getEconomy(userId);
  }

  async purchase({ userId, idempotencyKey, itemId }) {
    const result = await this.repository.createShopPurchase({
      purchaseId: uuidv7(),
      userId,
      itemId,
      idempotencyKey,
      payloadHash: payloadHash({ itemId }),
      purchasedAt: new Date().toISOString(),
    });
    if (result.status === 'conflict') throw new ApiError(409, 'shop.idempotency_key_reused', 'The idempotency key was reused with different purchase data.');
    if (result.status === 'not_found') throw new ApiError(404, 'shop.item_not_found', 'The shop item was not found.');
    if (result.status === 'unavailable') throw new ApiError(409, 'shop.item_unavailable', 'The shop item is unavailable.');
    if (result.status === 'owned') throw new ApiError(409, 'shop.item_owned', 'The shop item is already owned.');
    if (result.status === 'insufficient_balance') throw new ApiError(409, 'shop.insufficient_balance', 'The shell balance is insufficient.');
    return result;
  }
}
