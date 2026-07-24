package com.elenglish.studymentor.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheMetadataDao {

    @Upsert
    suspend fun upsert(entity: CacheMetadataEntity)

    @Query("SELECT * FROM cache_metadata WHERE resourceKey = :resourceKey")
    suspend fun findByKey(resourceKey: String): CacheMetadataEntity?

    @Query("SELECT * FROM cache_metadata ORDER BY resourceKey ASC")
    fun observeAll(): Flow<List<CacheMetadataEntity>>

    @Query("DELETE FROM cache_metadata WHERE resourceKey = :resourceKey")
    suspend fun deleteByKey(resourceKey: String)

    @Query("DELETE FROM cache_metadata")
    suspend fun clear()
}
