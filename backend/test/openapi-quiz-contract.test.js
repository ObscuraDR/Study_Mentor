import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const contract = JSON.parse(await readFile(new URL('../../contracts/openapi/ai-study-mentor.v1.openapi.json', import.meta.url), 'utf8'));

test('OpenAPI defines the minimum authenticated quiz and attempt surface', () => {
  assert.ok(contract.paths['/quizzes'].get);
  assert.ok(contract.paths['/quizzes/{quizId}'].get);
  assert.ok(contract.paths['/quiz-attempts'].post);
  for (const [path, method] of [['/quizzes', 'get'], ['/quizzes/{quizId}', 'get'], ['/quiz-attempts', 'post']]) {
    assert.deepEqual(contract.paths[path][method].security, [{ bearerAuth: [] }]);
  }
  assert.equal(contract.paths['/quizzes'].get.parameters[0].$ref, '#/components/parameters/LessonIdQuery');
  assert.ok(contract.paths['/quiz-attempts'].post.parameters.some((parameter) => parameter.$ref === '#/components/parameters/IdempotencyKey'));
  assert.ok(contract.paths['/quiz-attempts'].post.responses['201']);
  assert.ok(contract.paths['/quiz-attempts'].post.responses['200']);
  assert.ok(contract.paths['/quiz-attempts'].post.responses['409']);
});

test('OpenAPI quiz detail never exposes answer keys', () => {
  const option = contract.components.schemas.QuizAnswerOption;
  const question = contract.components.schemas.QuizQuestion;
  const detail = contract.components.schemas.QuizDetail;
  assert.equal(Object.hasOwn(option.properties, 'correct'), false);
  assert.equal(Object.hasOwn(option.properties, 'correctOptionId'), false);
  assert.equal(JSON.stringify(question).includes('correctOptionId'), false);
  assert.equal(JSON.stringify(detail).includes('correctOptionId'), false);
  assert.equal(JSON.stringify(detail).includes('"correct"'), false);
});

test('OpenAPI attempt request accepts only quiz id and selected options while result is server-derived', () => {
  const request = contract.components.schemas.QuizAttemptRequest;
  const answer = contract.components.schemas.QuizAttemptAnswerRequest;
  const result = contract.components.schemas.QuizAttemptResult;
  assert.deepEqual(request.required, ['quizId', 'answers']);
  assert.equal(request.additionalProperties, false);
  assert.deepEqual(answer.required, ['questionId', 'selectedOptionId']);
  assert.equal(answer.additionalProperties, false);
  for (const forbidden of ['score', 'scorePercentage', 'xpEarned', 'correctAnswers', 'correct', 'completion', 'level', 'achievements', 'submittedAt']) {
    assert.equal(Object.hasOwn(request.properties, forbidden), false);
    assert.equal(Object.hasOwn(answer.properties, forbidden), false);
  }
  assert.deepEqual(result.required, ['attemptId', 'quizId', 'submittedAt', 'totalQuestions', 'correctAnswers', 'scorePercentage', 'questionResults']);
  assert.match(result.properties.scorePercentage.description, /Server-computed/u);
});

test('OpenAPI defines canonical quiz errors', () => {
  const codes = contract.components.schemas.ErrorCode.enum;
  for (const code of ['quiz.not_found', 'quiz.inactive', 'quiz.lesson_inactive', 'quiz.invalid_submission', 'quiz.question_not_found', 'quiz.option_not_found', 'quiz.duplicate_answer', 'quiz.incomplete_attempt', 'quiz.idempotency_key_reused']) {
    assert.ok(codes.includes(code), code);
  }
});
