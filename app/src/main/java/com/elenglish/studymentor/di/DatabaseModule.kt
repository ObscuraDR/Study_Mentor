package com.elenglish.studymentor.di

import android.content.Context
import androidx.room.Room
import com.elenglish.studymentor.data.local.CacheMetadataDao
import com.elenglish.studymentor.data.local.CatalogDao
import com.elenglish.studymentor.data.local.StudyMentorDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StudyMentorDatabase =
        Room.databaseBuilder(context, StudyMentorDatabase::class.java, StudyMentorDatabase.NAME)
            // The cache is disposable: a schema change may drop and rebuild it,
            // because the backend remains the authority for all of this data.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCacheMetadataDao(database: StudyMentorDatabase): CacheMetadataDao =
        database.cacheMetadataDao()

    @Provides
    fun provideCatalogDao(database: StudyMentorDatabase): CatalogDao = database.catalogDao()
}
