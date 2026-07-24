package com.elenglish.studymentor.data.remote

import com.elenglish.studymentor.core.network.ApiEnvelope
import com.elenglish.studymentor.data.remote.dto.LessonDto
import com.elenglish.studymentor.data.remote.dto.SubjectDto
import com.elenglish.studymentor.data.remote.dto.TopicDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Learning catalog endpoints.
 *
 * All are authenticated reads. The backend returns only active rows, already
 * sorted by `display_order, id`; the client preserves that order exactly and
 * never re-sorts.
 */
interface CatalogApi {

    @GET("subjects")
    suspend fun listSubjects(): Response<ApiEnvelope<List<SubjectDto>>>

    @GET("subjects/{subjectId}")
    suspend fun getSubject(@Path("subjectId") subjectId: String): Response<ApiEnvelope<SubjectDto>>

    @GET("subjects/{subjectId}/topics")
    suspend fun listTopics(
        @Path("subjectId") subjectId: String,
    ): Response<ApiEnvelope<List<TopicDto>>>

    @GET("topics/{topicId}")
    suspend fun getTopic(@Path("topicId") topicId: String): Response<ApiEnvelope<TopicDto>>

    @GET("topics/{topicId}/lessons")
    suspend fun listLessons(
        @Path("topicId") topicId: String,
    ): Response<ApiEnvelope<List<LessonDto>>>

    @GET("lessons/{lessonId}")
    suspend fun getLesson(@Path("lessonId") lessonId: String): Response<ApiEnvelope<LessonDto>>
}
