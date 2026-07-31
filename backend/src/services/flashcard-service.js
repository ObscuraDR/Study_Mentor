import { createHash } from 'node:crypto';
import { v7 as uuidv7 } from 'uuid';
import { ApiError } from '../errors.js';

export const ALGORITHM_VERSION = 'leitner-5box-v1';

const MIN_BOX = 1;
const MAX_BOX = 5;
const MILLIS_PER_DAY = 24 * 60 * 60 * 1000;

function payloadHash(input) {
  return createHash('sha256').update(JSON.stringify(input)).digest('hex');
}

/**
 * leitner-5box-v1, ported verbatim from the Web implementation so no learner
 * sees a behaviour change.
 *
 *   known:  box = min(box + 1, 5); dueAt = reviewedAt + 2^(box - 1) days
 *   forgot: box = 1;               dueAt = reviewedAt
 *
 * Note the exponent uses the NEW box, so a first successful review schedules
 * two days out, not one. Scheduling is server-owned: no client value is read.
 */
export function applyLeitnerReview({ box, outcome, reviewedAt }) {
  const current = Math.min(Math.max(box ?? MIN_BOX, MIN_BOX), MAX_BOX);
  const reviewedAtMs = new Date(reviewedAt).getTime();

  if (outcome === 'forgot') {
    return { box: MIN_BOX, dueAt: new Date(reviewedAtMs).toISOString() };
  }

  const nextBox = Math.min(current + 1, MAX_BOX);
  const intervalDays = 2 ** (nextBox - 1);
  return {
    box: nextBox,
    dueAt: new Date(reviewedAtMs + intervalDays * MILLIS_PER_DAY).toISOString(),
  };
}

/** State for a card the learner has never reviewed: box 1, due immediately. */
export function initialReviewState(cardId, now) {
  return {
    cardId,
    box: MIN_BOX,
    dueAt: now,
    lastReviewedAt: null,
    totalReviews: 0,
    knownReviews: 0,
    algorithmVersion: ALGORITHM_VERSION,
  };
}

export class FlashcardService {
  constructor(repository, { now = () => new Date(), futureToleranceSeconds = 300 } = {}) {
    this.repository = repository;
    this.now = now;
    this.futureToleranceSeconds = futureToleranceSeconds;
  }

  async listDecks(lessonId) {
    const lesson = await this.repository.findLesson(lessonId);
    if (!lesson) throw new ApiError(404, 'flashcard.deck_not_found', 'The lesson does not exist or is inactive.');
    return this.repository.listFlashcardDecks(lessonId);
  }

  async getDeck(deckId) {
    const deck = await this.repository.findFlashcardDeck(deckId);
    if (!deck) throw new ApiError(404, 'flashcard.deck_not_found', 'The deck does not exist or is inactive.');
    const cards = await this.repository.listFlashcards(deckId);
    return { ...deck, cards };
  }

  async getQueue({ userId, deckId, dueOnly = true, limit = 20 }) {
    const deck = await this.repository.findFlashcardDeck(deckId);
    if (!deck) throw new ApiError(404, 'flashcard.deck_not_found', 'The deck does not exist or is inactive.');

    const now = this.now().toISOString();
    const cards = await this.repository.listFlashcards(deckId);
    const states = await this.repository.findFlashcardReviewStates(userId, cards.map((card) => card.id));
    const byCardId = new Map(states.map((state) => [state.cardId, state]));

    const entries = cards
      .map((card) => ({ card, state: byCardId.get(card.id) ?? initialReviewState(card.id, now) }))
      .filter((entry) => (dueOnly ? new Date(entry.state.dueAt).getTime() <= new Date(now).getTime() : true))
      .sort((a, b) => {
        const dueDiff = new Date(a.state.dueAt).getTime() - new Date(b.state.dueAt).getTime();
        return dueDiff !== 0 ? dueDiff : a.card.displayOrder - b.card.displayOrder;
      });

    return entries.slice(0, limit);
  }

  async submitReview({ userId, idempotencyKey, input }) {
    const card = await this.repository.findFlashcardForReview(input.cardId);
    if (!card) throw new ApiError(404, 'flashcard.card_not_found', 'The card does not exist or is inactive.');

    const nowMs = this.now().getTime();
    const reviewedAtMs = new Date(input.reviewedAt).getTime();
    if (reviewedAtMs > nowMs + this.futureToleranceSeconds * 1000) {
      throw new ApiError(422, 'flashcard.invalid_review', 'reviewedAt is too far in the future.');
    }

    const existingState = await this.repository.findFlashcardReviewState(userId, input.cardId);
    const currentBox = existingState?.box ?? MIN_BOX;
    const scheduled = applyLeitnerReview({
      box: currentBox,
      outcome: input.outcome,
      reviewedAt: input.reviewedAt,
    });

    const result = await this.repository.appendFlashcardReview({
      review: {
        id: uuidv7(),
        userId,
        cardId: input.cardId,
        outcome: input.outcome,
        reviewedAt: input.reviewedAt,
        resultingBox: scheduled.box,
        resultingDueAt: scheduled.dueAt,
        algorithmVersion: ALGORITHM_VERSION,
        idempotencyKey,
      },
      // Only client-owned fields are hashed. Derived scheduling is excluded so a
      // retry is recognised as the same submission rather than a conflict.
      payloadHash: payloadHash(input),
    });

    if (result.status === 'conflict') {
      throw new ApiError(409, 'flashcard.idempotency_key_reused', 'The idempotency key was already used with different review data.');
    }
    return result;
  }

  async resetDeck({ userId, deckId }) {
    const deck = await this.repository.findFlashcardDeck(deckId);
    if (!deck) throw new ApiError(404, 'flashcard.deck_not_found', 'The deck does not exist or is inactive.');
    const cardsReset = await this.repository.resetFlashcardDeckProgress(userId, deckId);
    return { deckId, cardsReset, resetAt: this.now().toISOString() };
  }
}
