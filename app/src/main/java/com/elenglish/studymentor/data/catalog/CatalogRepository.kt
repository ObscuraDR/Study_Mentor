package com.elenglish.studymentor.data.catalog

import com.elenglish.studymentor.core.network.ApiError
import com.elenglish.studymentor.core.network.ApiResult
import com.elenglish.studymentor.core.network.safeApiCall
import com.elenglish.studymentor.core.session.SessionScopedStore
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.data.local.CacheMetadataDao
import com.elenglish.studymentor.data.local.CacheMetadataEntity
import com.elenglish.studymentor.data.local.CachedLessonEntity
import com.elenglish.studymentor.data.local.CachedSubjectEntity
import com.elenglish.studymentor.data.local.CachedTopicEntity
import com.elenglish.studymentor.data.local.CatalogDao
import com.elenglish.studymentor.data.remote.CatalogApi
import com.elenglish.studymentor.data.remote.dto.LessonDto
import com.elenglish.studymentor.data.remote.dto.SubjectDto
import com.elenglish.studymentor.data.remote.dto.TopicDto
import com.elenglish.studymentor.domain.model.CatalogData
import com.elenglish.studymentor.domain.model.DataOrigin
import com.elenglish.studymentor.domain.model.Difficulty
import com.elenglish.studymentor.domain.model.Lesson
import com.elenglish.studymentor.domain.model.Subject
import com.elenglish.studymentor.domain.model.Topic
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Learning catalog reads.
 *
 * Policy: **network first, cache as a fallback.** A successful response replaces
 * the cached copy and is returned as [DataOrigin.Live]. When the device cannot
 * reach the backend, a stored copy is returned as [DataOrigin.Cached] together
 * with the time it was stored, so the UI can say so plainly. With neither, the
 * original failure is surfaced.
 *
 * A *backend* failure (401, 404, 5xx) is never masked by the cache: it is a real
 * answer about the current state, and hiding it behind stale rows would show the
 * user content they may no longer have access to.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val catalogApi: CatalogApi,
    private val catalogDao: CatalogDao,
    private val cacheMetadataDao: CacheMetadataDao,
    private val json: Json,
    private val timeSource: TimeSource,
) : SessionScopedStore {

    suspend fun getSubjects(): ApiResult<CatalogData<List<Subject>>> = readList(
        cacheKey = KEY_SUBJECTS,
        fetch = { safeApiCall(json, { catalogApi.listSubjects() }) { dtos -> dtos } },
        writeCache = { dtos ->
            catalogDao.replaceSubjects(
                dtos.mapIndexed { index, dto -> dto.toEntity(index) },
            )
        },
        readCache = { catalogDao.getSubjects().map(CachedSubjectEntity::toDomain) },
        mapLive = { dtos -> dtos.map(SubjectDto::toDomain) },
    )

    suspend fun getTopics(subjectId: String): ApiResult<CatalogData<List<Topic>>> = readList(
        cacheKey = keyTopics(subjectId),
        fetch = { safeApiCall(json, { catalogApi.listTopics(subjectId) }) { dtos -> dtos } },
        writeCache = { dtos ->
            catalogDao.replaceTopics(
                subjectId,
                dtos.mapIndexed { index, dto -> dto.toEntity(index) },
            )
        },
        readCache = { catalogDao.getTopics(subjectId).map(CachedTopicEntity::toDomain) },
        mapLive = { dtos -> dtos.map(TopicDto::toDomain) },
    )

    suspend fun getLessons(topicId: String): ApiResult<CatalogData<List<Lesson>>> = readList(
        cacheKey = keyLessons(topicId),
        fetch = { safeApiCall(json, { catalogApi.listLessons(topicId) }) { dtos -> dtos } },
        writeCache = { dtos ->
            catalogDao.replaceLessons(
                topicId,
                dtos.mapIndexed { index, dto -> dto.toEntity(index) },
            )
        },
        readCache = { catalogDao.getLessons(topicId).map(CachedLessonEntity::toDomain) },
        mapLive = { dtos -> dtos.map(LessonDto::toDomain) },
    )

    suspend fun getSubject(subjectId: String): ApiResult<CatalogData<Subject>> = readSingle(
        fetch = { safeApiCall(json, { catalogApi.getSubject(subjectId) }, SubjectDto::toDomain) },
        readCache = { catalogDao.findSubject(subjectId)?.toDomain() },
        cacheKey = KEY_SUBJECTS,
    )

    suspend fun getTopic(topicId: String): ApiResult<CatalogData<Topic>> = readSingle(
        fetch = { safeApiCall(json, { catalogApi.getTopic(topicId) }, TopicDto::toDomain) },
        readCache = { catalogDao.findTopic(topicId)?.toDomain() },
        cacheKey = null,
    )

    suspend fun getLesson(lessonId: String): ApiResult<CatalogData<Lesson>> = readSingle(
        fetch = { safeApiCall(json, { catalogApi.getLesson(lessonId) }, LessonDto::toDomain) },
        readCache = { catalogDao.findLesson(lessonId)?.toDomain() },
        cacheKey = null,
    )

    /** Drops every cached catalog row. Called when a session ends. */
    override suspend fun clearForSignOut() {
        catalogDao.clearAll()
        cacheMetadataDao.clear()
    }

    private suspend fun <Dto, Domain> readList(
        cacheKey: String,
        fetch: suspend () -> ApiResult<List<Dto>>,
        writeCache: suspend (List<Dto>) -> Unit,
        readCache: suspend () -> List<Domain>,
        mapLive: (List<Dto>) -> List<Domain>,
    ): ApiResult<CatalogData<List<Domain>>> = when (val result = fetch()) {
        is ApiResult.Success -> {
            writeCache(result.value)
            cacheMetadataDao.upsert(
                CacheMetadataEntity(cacheKey, timeSource.nowEpochMillis()),
            )
            ApiResult.Success(
                CatalogData(mapLive(result.value), DataOrigin.Live),
                result.requestId,
            )
        }

        is ApiResult.Failure -> if (result.error is ApiError.Network) {
            val cached = readCache()
            if (cached.isEmpty()) {
                result
            } else {
                ApiResult.Success(
                    CatalogData(
                        value = cached,
                        origin = DataOrigin.Cached,
                        cachedAtEpochMillis = cacheMetadataDao.findByKey(cacheKey)
                            ?.fetchedAtEpochMillis,
                    ),
                )
            }
        } else {
            result
        }
    }

    private suspend fun <Domain> readSingle(
        fetch: suspend () -> ApiResult<Domain>,
        readCache: suspend () -> Domain?,
        cacheKey: String?,
    ): ApiResult<CatalogData<Domain>> = when (val result = fetch()) {
        is ApiResult.Success ->
            ApiResult.Success(CatalogData(result.value, DataOrigin.Live), result.requestId)

        is ApiResult.Failure -> if (result.error is ApiError.Network) {
            val cached = readCache()
            if (cached == null) {
                result
            } else {
                ApiResult.Success(
                    CatalogData(
                        value = cached,
                        origin = DataOrigin.Cached,
                        cachedAtEpochMillis = cacheKey
                            ?.let { cacheMetadataDao.findByKey(it)?.fetchedAtEpochMillis },
                    ),
                )
            }
        } else {
            result
        }
    }

    companion object {
        const val KEY_SUBJECTS = "catalog.subjects"
        fun keyTopics(subjectId: String) = "catalog.topics.$subjectId"
        fun keyLessons(topicId: String) = "catalog.lessons.$topicId"
    }
}

// Mapping ---------------------------------------------------------------------

private fun SubjectDto.toDomain() = Subject(id, slug, name, displayOrder)

private fun TopicDto.toDomain() = Topic(id, subjectId, slug, name, displayOrder)

private fun LessonDto.toDomain() = Lesson(
    id = id,
    topicId = topicId,
    slug = slug,
    title = title,
    description = description,
    estimatedMinutes = estimatedMinutes,
    difficulty = Difficulty.fromWire(difficulty),
    displayOrder = displayOrder,
)

private fun SubjectDto.toEntity(position: Int) =
    CachedSubjectEntity(id, slug, name, displayOrder, position)

private fun TopicDto.toEntity(position: Int) =
    CachedTopicEntity(id, subjectId, slug, name, displayOrder, position)

private fun LessonDto.toEntity(position: Int) = CachedLessonEntity(
    id = id,
    topicId = topicId,
    slug = slug,
    title = title,
    description = description,
    estimatedMinutes = estimatedMinutes,
    difficulty = difficulty,
    displayOrder = displayOrder,
    position = position,
)

private fun CachedSubjectEntity.toDomain() = Subject(id, slug, name, displayOrder)

private fun CachedTopicEntity.toDomain() = Topic(id, subjectId, slug, name, displayOrder)

private fun CachedLessonEntity.toDomain() = Lesson(
    id = id,
    topicId = topicId,
    slug = slug,
    title = title,
    description = description,
    estimatedMinutes = estimatedMinutes,
    difficulty = Difficulty.fromWire(difficulty),
    displayOrder = displayOrder,
)
