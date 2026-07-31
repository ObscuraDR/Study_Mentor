package com.elenglish.studymentor.data.fullproduct

import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.network.safeApiCall
import com.elenglish.studymentor.core.network.safeApiCallWithStatus
import com.elenglish.studymentor.core.uuid.UuidGenerator
import com.elenglish.studymentor.data.remote.FullProductApi
import com.elenglish.studymentor.data.remote.dto.BossAnswerRequestDto
import com.elenglish.studymentor.data.remote.dto.BossAttemptRequestDto
import com.elenglish.studymentor.data.remote.dto.BossAttemptResultDto
import com.elenglish.studymentor.data.remote.dto.BossChallengeDto
import com.elenglish.studymentor.data.remote.dto.EconomyProjectionDto
import com.elenglish.studymentor.data.remote.dto.InventoryItemDto
import com.elenglish.studymentor.data.remote.dto.PurchaseRequestDto
import com.elenglish.studymentor.data.remote.dto.PurchaseResultDto
import com.elenglish.studymentor.data.remote.dto.ShopItemDto
import com.elenglish.studymentor.domain.model.BossAnswer
import com.elenglish.studymentor.domain.model.BossAttemptResult
import com.elenglish.studymentor.domain.model.BossChallenge
import com.elenglish.studymentor.domain.model.BossOption
import com.elenglish.studymentor.domain.model.BossQuestion
import com.elenglish.studymentor.domain.model.EconomyProjection
import com.elenglish.studymentor.domain.model.InventoryItem
import com.elenglish.studymentor.domain.model.PendingBossAttempt
import com.elenglish.studymentor.domain.model.PendingPurchase
import com.elenglish.studymentor.domain.model.PurchaseResult
import com.elenglish.studymentor.domain.model.ShopItem
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FullProductRepository @Inject constructor(
    private val api: FullProductApi,
    private val uuidGenerator: UuidGenerator,
    private val json: Json,
) {
    suspend fun getActiveBossChallenge(): ApiResult<BossChallenge> = safeApiCall(
        json = json,
        block = { api.getActiveBossChallenge() },
        transform = BossChallengeDto::toDomain,
    )

    fun prepareBossAttempt(challengeId: String, answers: List<BossAnswer>): PendingBossAttempt {
        require(answers.isNotEmpty()) { "a boss attempt must contain answers" }
        return PendingBossAttempt(uuidGenerator.newUuidV7(), challengeId, answers)
    }

    suspend fun submitBossAttempt(pending: PendingBossAttempt): ApiResult<BossAttemptResult> =
        safeApiCallWithStatus(
            json = json,
            block = {
                api.submitBossAttempt(
                    pending.idempotencyKey,
                    BossAttemptRequestDto(
                        pending.challengeId,
                        pending.answers.map { BossAnswerRequestDto(it.questionId, it.selectedOptionId) },
                    ),
                )
            },
            transform = { status, dto -> dto.toDomain(status == HTTP_OK) },
        )

    suspend fun getEconomy(): ApiResult<EconomyProjection> = safeApiCall(
        json = json,
        block = { api.getEconomy() },
        transform = EconomyProjectionDto::toDomain,
    )

    fun preparePurchase(itemId: String) = PendingPurchase(uuidGenerator.newUuidV7(), itemId)

    suspend fun purchase(pending: PendingPurchase): ApiResult<PurchaseResult> =
        safeApiCallWithStatus(
            json = json,
            block = {
                api.purchase(
                    pending.idempotencyKey,
                    PurchaseRequestDto(pending.itemId),
                )
            },
            transform = { status, dto -> dto.toDomain(status == HTTP_OK) },
        )

    private companion object {
        const val HTTP_OK = 200
    }
}

private fun BossChallengeDto.toDomain() = BossChallenge(
    id, zoneId, title, description, questionCount, passingPercentage, rewardShells,
    available, questions.map { question ->
        BossQuestion(
            question.id,
            question.prompt,
            question.displayOrder,
            question.options.map { BossOption(it.id, it.text, it.displayOrder) },
        )
    },
)

private fun BossAttemptResultDto.toDomain(wasReplay: Boolean) = BossAttemptResult(
    attemptId, challengeId, submittedAt, totalQuestions, correctAnswers,
    scorePercentage, passed, rewardShells, walletBalance, wasReplay,
)

private fun EconomyProjectionDto.toDomain() = EconomyProjection(
    currency,
    balance,
    shopItems.map(ShopItemDto::toDomain),
    inventory.map(InventoryItemDto::toDomain),
)

private fun ShopItemDto.toDomain() =
    ShopItem(id, name, description, priceShells, available, owned)

private fun InventoryItemDto.toDomain() = InventoryItem(itemId, quantity, equipped)

private fun PurchaseResultDto.toDomain(wasReplay: Boolean) = PurchaseResult(
    purchaseId, itemId, priceShells, balance, inventoryQuantity, purchasedAt, wasReplay,
)
