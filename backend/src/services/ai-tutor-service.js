import { createHash } from 'node:crypto';
import { ApiError } from '../errors.js';
import { withTutorAdmissionLock } from '../admission-mutex.js';

function requestFingerprint(lessonId, message) {
  return createHash('sha256').update(JSON.stringify({ lessonId, message })).digest('hex');
}

export class AiTutorService {
  constructor({ repository, rateAdmission, fakeProvider, config }) {
    this.repository = repository;
    this.rateAdmission = rateAdmission;
    this.fakeProvider = fakeProvider;
    this.config = {
      aiLeaseDurationSeconds: config.aiLeaseDurationSeconds ?? 30,
      concurrencyLimit: config.aiConcurrencyLimit ?? 1,
      providerTimeoutMs: config.aiProviderTimeoutMs ?? 15000,
    };
  }

  async generateTutorResponse({ userId, idempotencyKey, lessonId, message, now = new Date() }) {
    const fingerprint = requestFingerprint(lessonId, message);

    // 1. Validate lesson existence
    const lessonCtx = await this.repository.findTutorLessonContext(lessonId);
    if (!lessonCtx) throw new ApiError(404, 'learning.resource_not_found', 'The lesson does not exist or is inactive.');

    // 2. Atomic admission
    const admissionResult = await withTutorAdmissionLock(userId, async () => {
      const dbResult = await this.repository.admitTutorProviderCall({
        userId,
        idempotencyKey,
        requestFingerprint: fingerprint,
        lessonId,
        now,
        concurrencyLimit: this.config.concurrencyLimit,
        leaseDurationSeconds: this.config.aiLeaseDurationSeconds,
      });

      if (dbResult.outcome === 'fingerprintMismatch') {
        throw new ApiError(409, 'ai.idempotency_key_reused', 'The idempotency key was already used with a different request.');
      }

      if (dbResult.outcome === 'completedReplay') {
        return { status: 'replayed', result: dbResult.result };
      }

      if (dbResult.outcome === 'activeProcessing') {
        throw new ApiError(429, 'rate_limit.exceeded', 'A request with this idempotency key is already being processed.');
      }

      if (dbResult.outcome === 'claimCreated' || dbResult.outcome === 'claimReclaimed') {
        // Rate evaluation for genuine new/reclaimed claims
        if (!this.rateAdmission.evaluateAll(userId)) {
          // Rate rejected after DB claim was made — must delete the row
          await this.repository.releaseTutorRequest({
            userId,
            idempotencyKey,
            claimToken: dbResult.row.claimToken,
          });
          return { status: 'rateRejected' };
        }
        // Admission passed. Consume counters.
        this.rateAdmission.consumeAdmission(userId);
        return {
          status: dbResult.outcome === 'claimCreated' ? 'claimCreated' : 'claimReclaimed',
          row: dbResult.row,
        };
      }

      if (dbResult.outcome === 'concurrencyRejected') {
        return { status: 'concurrencyRejected' };
      }

      return { status: 'rateRejected' };
    });

    // Handle outcomes from admission
    if (admissionResult.status === 'replayed') {
      return {
        response: admissionResult.result,
        created: false,
      };
    }

    if (admissionResult.status === 'rateRejected' || admissionResult.status === 'concurrencyRejected') {
      throw new ApiError(429, 'rate_limit.exceeded', 'Too many requests. Please try again later.');
    }

    // 4. Claimed — invoke provider outside mutex and transaction
    const { row } = admissionResult;

    try {
      const providerResponse = await this.fakeProvider.generate({
        lessonId,
        message,
        lessonContext: lessonCtx,
        timeoutMs: this.config.providerTimeoutMs,
      });

      // 5. Normalize the response
      const normalized = this.normalizeResponse(providerResponse);

      // 6. Complete
      const completed = await this.repository.completeTutorRequest({
        userId,
        idempotencyKey,
        claimToken: row.claimToken,
        answer: normalized.answer,
        status: normalized.status,
        now: new Date(),
      });

      if (!completed) {
        throw new ApiError(500, 'server.internal', 'Failed to finalize the tutor response.');
      }

      return {
        response: completed.result,
        created: admissionResult.status === 'claimCreated',
      };
    } catch (error) {
      if (error instanceof ApiError && error.status !== 500) {
        // Pre-existing ApiError — release the claim
        await this.repository.releaseTutorRequest({
          userId,
          idempotencyKey,
          claimToken: row.claimToken,
        });
        throw error;
      }

      // Provider failure — release and map to canonical error
      await this.repository.releaseTutorRequest({
        userId,
        idempotencyKey,
        claimToken: row.claimToken,
      });

      if (error.code === 'PROVIDER_TIMEOUT') {
        throw new ApiError(504, 'ai.timeout', 'The AI provider did not respond in time.');
      }

      if (error.code === 'PROVIDER_ERROR') {
        throw new ApiError(502, 'ai.provider_error', 'The AI provider returned an error.');
      }

      throw new ApiError(502, 'ai.provider_error', 'The AI provider returned an unexpected error.');
    }
  }

  normalizeResponse(providerResponse) {
    if (providerResponse.refused) {
      return {
        answer: providerResponse.answer || 'I cannot help with that request because it is outside the scope of English language instruction.',
        status: 'refused',
      };
    }

    if (providerResponse.truncated) {
      return {
        answer: providerResponse.answer,
        status: 'truncated',
      };
    }

    return {
      answer: providerResponse.answer,
      status: 'completed',
    };
  }
}
