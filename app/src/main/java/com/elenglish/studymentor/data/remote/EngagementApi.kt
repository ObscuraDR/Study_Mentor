package com.elenglish.studymentor.data.remote

import com.elenglish.studymentor.core.network.ApiEnvelope
import com.elenglish.studymentor.data.remote.dto.EngagementProjectionDto
import retrofit2.Response
import retrofit2.http.GET

interface EngagementApi {

    /**
     * The backend's engagement read model: level/XP, streak, private
     * achievements and daily/weekly missions. There is no corresponding write
     * endpoint — the client never submits XP, level, streak, achievements or
     * mission progress.
     */
    @GET("me/engagement")
    suspend fun getEngagement(): Response<ApiEnvelope<EngagementProjectionDto>>
}
