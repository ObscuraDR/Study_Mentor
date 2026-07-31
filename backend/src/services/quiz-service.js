import { createHash } from 'node:crypto';
import { v7 as uuidv7 } from 'uuid';
import { ApiError } from '../errors.js';

function payloadHash(input) {
  return createHash('sha256').update(JSON.stringify(normalizedAttemptInput(input))).digest('hex');
}

function normalizedAttemptInput(input) {
  return {
    quizId: input.quizId,
    answers: [...input.answers]
      .sort((left, right) => left.questionId.localeCompare(right.questionId))
      .map((answer) => ({ questionId: answer.questionId, selectedOptionId: answer.selectedOptionId })),
  };
}

function roundedPercent(correct, total) {
  return total === 0 ? 0 : Math.round((correct / total) * 10_000) / 100;
}

export class QuizService {
  constructor(repository, { now = () => new Date() } = {}) {
    this.repository = repository;
    this.now = now;
  }

  async listQuizzes(lessonId) {
    const lesson = await this.repository.findLesson(lessonId);
    if (!lesson) throw new ApiError(404, 'quiz.lesson_inactive', 'The lesson does not exist or is inactive.');
    return this.repository.listQuizzesByLesson(lessonId);
  }

  async getQuiz(quizId) {
    const quiz = await this.repository.findQuizDetail(quizId);
    if (!quiz) throw new ApiError(404, 'quiz.not_found', 'The quiz was not found.');
    return quiz;
  }

  async submitAttempt({ userId, idempotencyKey, input }) {
    const source = await this.repository.findQuizAttemptSource(input.quizId);
    this.assertAvailable(source);
    const answers = this.score(source, input.answers);
    const totalQuestions = source.questions.length;
    const correctAnswers = answers.filter((answer) => answer.correct).length;
    const submittedAt = this.now().toISOString();
    const result = {
      attemptId: uuidv7(),
      quizId: source.id,
      submittedAt,
      totalQuestions,
      correctAnswers,
      scorePercentage: roundedPercent(correctAnswers, totalQuestions),
      questionResults: answers.map((answer) => ({
        questionId: answer.questionId,
        selectedOptionId: answer.selectedOptionId,
        correct: answer.correct,
        correctOptionId: answer.correctOptionId,
      })),
    };
    const attempt = {
      id: result.attemptId,
      userId,
      quizId: source.id,
      submittedAt,
      totalQuestions,
      correctAnswers,
      scorePercentage: result.scorePercentage,
      idempotencyKey,
      result,
    };
    const saved = await this.repository.createQuizAttempt({
      attempt,
      answers,
      payloadHash: payloadHash(input),
    });
    if (saved.status === 'conflict') throw new ApiError(409, 'quiz.idempotency_key_reused', 'The idempotency key was already used with different attempt data.');
    return saved;
  }

  assertAvailable(source) {
    if (!source) throw new ApiError(404, 'quiz.not_found', 'The quiz was not found.');
    if (!source.active) throw new ApiError(422, 'quiz.inactive', 'The quiz is inactive.');
    if (!source.lessonActive || !source.topicActive || !source.subjectActive) throw new ApiError(422, 'quiz.lesson_inactive', 'The quiz lesson is inactive.');
    if (!source.questions.length) throw new ApiError(422, 'quiz.invalid_submission', 'The quiz has no active questions.');
  }

  score(source, submittedAnswers) {
    const submittedByQuestion = new Map();
    for (const answer of submittedAnswers) {
      if (submittedByQuestion.has(answer.questionId)) throw new ApiError(422, 'quiz.duplicate_answer', 'Each quiz question may be answered only once.');
      submittedByQuestion.set(answer.questionId, answer.selectedOptionId);
    }
    if (submittedByQuestion.size !== source.questions.length) throw new ApiError(422, 'quiz.incomplete_attempt', 'All quiz questions must be answered.');

    const questionById = new Map(source.questions.map((question) => [question.id, question]));
    const answers = [];
    for (const [questionId, selectedOptionId] of submittedByQuestion) {
      const question = questionById.get(questionId);
      if (!question) throw new ApiError(422, 'quiz.question_not_found', 'The submitted question does not belong to this quiz.');
      const selectedOption = question.options.find((option) => option.id === selectedOptionId);
      if (!selectedOption) throw new ApiError(422, 'quiz.option_not_found', 'The submitted answer option does not belong to this question.');
      const correctOption = question.options.find((option) => option.correct);
      if (!correctOption) throw new ApiError(422, 'quiz.invalid_submission', 'The quiz question is not objectively scorable.');
      answers.push({
        questionId,
        selectedOptionId,
        correct: selectedOptionId === correctOption.id,
        correctOptionId: correctOption.id,
      });
    }
    return source.questions.map((question) => answers.find((answer) => answer.questionId === question.id));
  }
}
