import assert from 'node:assert/strict';
import test from 'node:test';
import { QuizService } from '../src/services/quiz-service.js';
import { MemoryIdentityRepository } from './support/memory-identity-repository.js';

const userId = '019f7e39-0200-7000-8000-000000000001';
const quizId = '019f7e39-0006-7000-8000-000000000001';
const inactiveQuizId = '019f7e39-0015-7000-8000-000000000001';
const questionOne = '019f7e39-0007-7000-8000-000000000001';
const questionTwo = '019f7e39-0008-7000-8000-000000000001';
const inactiveQuestion = '019f7e39-0016-7000-8000-000000000001';
const correctOne = '019f7e39-0009-7000-8000-000000000001';
const wrongOne = '019f7e39-0010-7000-8000-000000000001';
const correctTwo = '019f7e39-0013-7000-8000-000000000001';
const wrongTwo = '019f7e39-0012-7000-8000-000000000001';

function service() {
  return new QuizService(new MemoryIdentityRepository(), { now: () => new Date('2026-07-20T08:00:00.000Z') });
}

function attempt(answers = [
  { questionId: questionOne, selectedOptionId: correctOne },
  { questionId: questionTwo, selectedOptionId: correctTwo },
]) {
  return { quizId, answers };
}

test('quiz service returns active catalog and detail without answer keys', async () => {
  const current = service();
  const list = await current.listQuizzes('019f7e39-0003-7000-8000-000000000001');
  assert.equal(list.length, 1);
  assert.equal(list[0].questionCount, 2);
  const detail = await current.getQuiz(quizId);
  assert.equal(detail.questions.length, 2);
  assert.equal(detail.questions[0].options[0].text, 'Good morning');
  assert.equal(Object.hasOwn(detail.questions[0].options[0], 'correct'), false);
});

test('quiz service scores all-correct, partial, and zero-correct attempts deterministically', async () => {
  const current = service();
  const all = await current.submitAttempt({ userId, idempotencyKey: '019f7e39-0201-7000-8000-000000000001', input: attempt() });
  assert.equal(all.status, 'created');
  assert.equal(all.result.correctAnswers, 2);
  assert.equal(all.result.scorePercentage, 100);
  assert.equal(all.result.questionResults[0].correctOptionId, correctOne);

  const partial = await current.submitAttempt({ userId, idempotencyKey: '019f7e39-0202-7000-8000-000000000001', input: attempt([{ questionId: questionOne, selectedOptionId: wrongOne }, { questionId: questionTwo, selectedOptionId: correctTwo }]) });
  assert.equal(partial.result.correctAnswers, 1);
  assert.equal(partial.result.scorePercentage, 50);

  const none = await current.submitAttempt({ userId, idempotencyKey: '019f7e39-0203-7000-8000-000000000001', input: attempt([{ questionId: questionOne, selectedOptionId: wrongOne }, { questionId: questionTwo, selectedOptionId: wrongTwo }]) });
  assert.equal(none.result.correctAnswers, 0);
  assert.equal(none.result.scorePercentage, 0);
});

test('quiz service rejects invalid semantic submissions with canonical quiz errors', async () => {
  const cases = [
    [attempt([{ questionId: questionOne, selectedOptionId: correctOne }]), 'quiz.incomplete_attempt'],
    [attempt([{ questionId: questionOne, selectedOptionId: correctOne }, { questionId: questionOne, selectedOptionId: wrongOne }]), 'quiz.duplicate_answer'],
    [attempt([{ questionId: inactiveQuestion, selectedOptionId: correctOne }, { questionId: questionTwo, selectedOptionId: correctTwo }]), 'quiz.question_not_found'],
    [attempt([{ questionId: questionOne, selectedOptionId: correctTwo }, { questionId: questionTwo, selectedOptionId: correctTwo }]), 'quiz.option_not_found'],
    [{ quizId: inactiveQuizId, answers: [{ questionId: inactiveQuestion, selectedOptionId: '019f7e39-0017-7000-8000-000000000001' }] }, 'quiz.inactive'],
  ];
  for (const [input, code] of cases) {
    await assert.rejects(
      service().submitAttempt({ userId, idempotencyKey: '019f7e39-0210-7000-8000-000000000001', input }),
      (error) => error.code === code,
    );
  }
});

test('quiz attempt idempotency replays equivalent payloads and rejects mismatched reuse', async () => {
  const current = service();
  const key = '019f7e39-0204-7000-8000-000000000001';
  const first = await current.submitAttempt({ userId, idempotencyKey: key, input: attempt() });
  const replay = await current.submitAttempt({ userId, idempotencyKey: key, input: attempt([...attempt().answers].reverse()) });
  assert.equal(replay.status, 'replayed');
  assert.equal(replay.result.attemptId, first.result.attemptId);
  await assert.rejects(
    current.submitAttempt({ userId, idempotencyKey: key, input: attempt([{ questionId: questionOne, selectedOptionId: wrongOne }, { questionId: questionTwo, selectedOptionId: correctTwo }]) }),
    (error) => error.code === 'quiz.idempotency_key_reused',
  );
});
