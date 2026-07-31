package com.elenglish.studymentor.data.remote

import com.elenglish.studymentor.core.network.ApiEnvelope
import com.elenglish.studymentor.data.remote.dto.CampaignProjectionDto
import retrofit2.Response
import retrofit2.http.GET

/** Private, read-only backend campaign projection. */
interface CampaignApi {
    @GET("me/campaign")
    suspend fun getCampaign(): Response<ApiEnvelope<CampaignProjectionDto>>
}
