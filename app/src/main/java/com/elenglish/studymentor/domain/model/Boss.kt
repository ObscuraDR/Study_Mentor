package com.elenglish.studymentor.domain.model

data class BossOption(
    val id: String,
    val text: String,
    val displayOrder: Int,
)

/** No answer key is exposed in the boss read contract. */
data class BossQuestion(
    val id: String,
    val prompt: String,
    val displayOrder: Int,
    val options: List<BossOption>,
)

data class BossChallenge(
    val id: String,
    val zoneId: String,
    val title: String,
    val description: String?,
    val questionCount: Int,
    val passingPercentage: Double,
    val rewardShells: Int,
    val available: Boolean,
    val questions: List<BossQuestion>,
)

data class BossAnswer(
    val questionId: String,
    val selectedOptionId: String,
)

data class PendingBossAttempt(
    val idempotencyKey: String,
    val challengeId: String,
    val answers: List<BossAnswer>,
)

data class BossAttemptResult(
    val attemptId: String,
    val challengeId: String,
    val submittedAt: String,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val scorePercentage: Double,
    val passed: Boolean,
    val rewardShells: Int,
    val walletBalance: Int,
    val wasReplay: Boolean,
)
