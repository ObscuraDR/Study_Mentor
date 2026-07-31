package com.elenglish.studymentor.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class WrongAnswerDto(
    val questionId: String,
    val quizId: String,
    val quizTitle: String,
    val lessonId: String,
    val prompt: String,
    val selectedOptionId: String,
    val selectedOptionText: String,
    val correctOptionId: String,
    val correctOptionText: String,
    val lastAnsweredAt: String,
    val wrongCount: Int,
)

@Serializable
data class WrongAnswerPageDto(
    val items: List<WrongAnswerDto>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val hasNext: Boolean,
)

@Serializable
data class BossOptionDto(val id: String, val text: String, val displayOrder: Int)

@Serializable
data class BossQuestionDto(
    val id: String,
    val prompt: String,
    val displayOrder: Int,
    val options: List<BossOptionDto>,
)

@Serializable
data class BossChallengeDto(
    val id: String,
    val zoneId: String,
    val title: String,
    val description: String? = null,
    val questionCount: Int,
    val passingPercentage: Double,
    val rewardShells: Int,
    val available: Boolean,
    val questions: List<BossQuestionDto>,
)

@Serializable
data class BossAnswerRequestDto(val questionId: String, val selectedOptionId: String)

@Serializable
data class BossAttemptRequestDto(
    val challengeId: String,
    val answers: List<BossAnswerRequestDto>,
)

@Serializable
data class BossAttemptResultDto(
    val attemptId: String,
    val challengeId: String,
    val submittedAt: String,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val scorePercentage: Double,
    val passed: Boolean,
    val rewardShells: Int,
    val walletBalance: Int,
)

@Serializable
data class ShopItemDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val priceShells: Int,
    val available: Boolean,
    val owned: Boolean,
)

@Serializable
data class InventoryItemDto(
    val itemId: String,
    val quantity: Int,
    val equipped: Boolean,
)

@Serializable
data class EconomyProjectionDto(
    val currency: String,
    val balance: Int,
    val shopItems: List<ShopItemDto>,
    val inventory: List<InventoryItemDto>,
)

@Serializable
data class PurchaseRequestDto(val itemId: String)

@Serializable
data class PurchaseResultDto(
    val purchaseId: String,
    val itemId: String,
    val priceShells: Int,
    val balance: Int,
    val inventoryQuantity: Int,
    val purchasedAt: String,
)
