package com.elenglish.studymentor.data.remote

import com.elenglish.studymentor.core.network.ApiEnvelope
import com.elenglish.studymentor.data.remote.dto.FlashcardDeckDetailDto
import com.elenglish.studymentor.data.remote.dto.FlashcardDeckDto
import com.elenglish.studymentor.data.remote.dto.FlashcardDeckResetResultDto
import com.elenglish.studymentor.data.remote.dto.FlashcardQueueEntryDto
import com.elenglish.studymentor.data.remote.dto.FlashcardReviewRequestDto
import com.elenglish.studymentor.data.remote.dto.FlashcardReviewResultDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface FlashcardApi {

    @GET("flashcard-decks")
    suspend fun listDecks(
        @Query("lessonId") lessonId: String,
    ): Response<ApiEnvelope<List<FlashcardDeckDto>>>

    @GET("flashcard-decks/{deckId}")
    suspend fun getDeck(
        @Path("deckId") deckId: String,
    ): Response<ApiEnvelope<FlashcardDeckDetailDto>>

    @GET("me/flashcard-queue")
    suspend fun getQueue(
        @Query("deckId") deckId: String,
        @Query("dueOnly") dueOnly: Boolean = true,
    ): Response<ApiEnvelope<List<FlashcardQueueEntryDto>>>

    /**
     * Submit one review outcome. `201` new, `200` replay, `409` key reused with
     * different data. The client sends only the card, the outcome and when it
     * happened; the box and due date come back from the server.
     */
    @POST("flashcard-reviews")
    suspend fun submitReview(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: FlashcardReviewRequestDto,
    ): Response<ApiEnvelope<FlashcardReviewResultDto>>

    @POST("me/flashcard-decks/{deckId}/reset")
    suspend fun resetDeck(
        @Path("deckId") deckId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Response<ApiEnvelope<FlashcardDeckResetResultDto>>
}
