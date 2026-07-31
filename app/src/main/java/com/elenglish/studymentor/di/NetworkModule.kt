package com.elenglish.studymentor.di

import android.content.Context
import android.content.SharedPreferences
import com.elenglish.studymentor.BuildConfig
import com.elenglish.studymentor.core.network.AccessTokenInterceptor
import com.elenglish.studymentor.core.network.ClientPlatformInterceptor
import com.elenglish.studymentor.core.network.RefreshTokenAuthenticator
import com.elenglish.studymentor.core.session.SessionScopedStore
import com.elenglish.studymentor.core.time.SystemTimeSource
import com.elenglish.studymentor.core.time.TimeSource
import com.elenglish.studymentor.core.uuid.UuidGenerator
import com.elenglish.studymentor.core.uuid.UuidV7Generator
import com.elenglish.studymentor.data.account.AccountRepository
import com.elenglish.studymentor.data.catalog.CatalogRepository
import com.elenglish.studymentor.data.learning.CompletedLessonsRegistry
import com.elenglish.studymentor.data.remote.AccountApi
import com.elenglish.studymentor.data.remote.AuthApi
import com.elenglish.studymentor.data.remote.CatalogApi
import com.elenglish.studymentor.data.remote.CampaignApi
import com.elenglish.studymentor.data.remote.EngagementApi
import com.elenglish.studymentor.data.remote.StreakRecoveryApi
import com.elenglish.studymentor.data.remote.LearningApi
import com.elenglish.studymentor.data.remote.QuizApi
import com.elenglish.studymentor.data.remote.FlashcardApi
import com.elenglish.studymentor.data.remote.FullProductApi
import com.elenglish.studymentor.data.remote.TutorApi
import com.elenglish.studymentor.notifications.ReminderScheduler
import com.elenglish.studymentor.data.session.KeystoreRefreshTokenStorage
import com.elenglish.studymentor.data.session.RefreshTokenStorage
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Marks the preferences file that holds the encrypted refresh token. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SecureSessionPreferences

/**
 * HTTP/JSON transport for OpenAPI v1.
 *
 * Logging policy: bodies and headers are never logged. Debug builds use
 * [HttpLoggingInterceptor.Level.BASIC] (method, URL, status, duration) so that
 * access tokens, refresh tokens, `Authorization` headers and login/tutor payloads
 * cannot reach logcat. Release builds log nothing.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = false
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.ENABLE_NETWORK_LOGGING) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        clientPlatformInterceptor: ClientPlatformInterceptor,
        accessTokenInterceptor: AccessTokenInterceptor,
        refreshTokenAuthenticator: RefreshTokenAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(clientPlatformInterceptor)
        .addInterceptor(accessTokenInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(refreshTokenAuthenticator)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideAccountApi(retrofit: Retrofit): AccountApi = retrofit.create(AccountApi::class.java)

    @Provides
    @Singleton
    fun provideCatalogApi(retrofit: Retrofit): CatalogApi = retrofit.create(CatalogApi::class.java)

    @Provides
    @Singleton
    fun provideCampaignApi(retrofit: Retrofit): CampaignApi = retrofit.create(CampaignApi::class.java)

    @Provides
    @Singleton
    fun provideLearningApi(retrofit: Retrofit): LearningApi =
        retrofit.create(LearningApi::class.java)

    @Provides
    @Singleton
    fun provideQuizApi(retrofit: Retrofit): QuizApi = retrofit.create(QuizApi::class.java)

    @Provides
    @Singleton
    fun provideEngagementApi(retrofit: Retrofit): EngagementApi =
        retrofit.create(EngagementApi::class.java)

    @Provides
    @Singleton
    fun provideStreakRecoveryApi(retrofit: Retrofit): StreakRecoveryApi =
        retrofit.create(StreakRecoveryApi::class.java)

    @Provides
    @Singleton
    fun provideTutorApi(retrofit: Retrofit): TutorApi = retrofit.create(TutorApi::class.java)

    @Provides
    @Singleton
    fun provideFlashcardApi(retrofit: Retrofit): FlashcardApi =
        retrofit.create(FlashcardApi::class.java)

    @Provides
    @Singleton
    fun provideFullProductApi(retrofit: Retrofit): FullProductApi =
        retrofit.create(FullProductApi::class.java)

    /** Keystore-backed store for the refresh token. */
    @Provides
    @Singleton
    @SecureSessionPreferences
    fun provideSecureSessionPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences = KeystoreRefreshTokenStorage.createEncryptedPreferences(context)

    @Provides
    @Singleton
    fun provideKeystoreRefreshTokenStorage(
        @SecureSessionPreferences preferences: SharedPreferences,
    ): KeystoreRefreshTokenStorage = KeystoreRefreshTokenStorage(preferences)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionStorageModule {

    @Binds
    @Singleton
    abstract fun bindRefreshTokenStorage(
        implementation: KeystoreRefreshTokenStorage,
    ): RefreshTokenStorage

    /**
     * Every user-scoped local store joins this set and is wiped on sign-out.
     * Adding a new cache means adding one `@Binds @IntoSet` here — it cannot be
     * forgotten in a sign-out code path.
     */
    @Binds
    @IntoSet
    abstract fun bindCatalogCacheAsSessionScoped(
        implementation: CatalogRepository,
    ): SessionScopedStore

    @Binds
    @IntoSet
    abstract fun bindCompletedLessonsAsSessionScoped(
        implementation: CompletedLessonsRegistry,
    ): SessionScopedStore

    /** Reminders are a signed-in feature, so signing out cancels them. */
    @Binds
    @IntoSet
    abstract fun bindReminderSchedulerAsSessionScoped(
        implementation: ReminderScheduler,
    ): SessionScopedStore

    /**
     * Profile and settings cache must be wiped on sign-out so one user's data
     * never bleeds into the session of whoever signs in next on the device.
     */
    @Binds
    @IntoSet
    abstract fun bindAccountRepositoryAsSessionScoped(
        implementation: AccountRepository,
    ): SessionScopedStore

    @Binds
    @Singleton
    abstract fun bindTimeSource(implementation: SystemTimeSource): TimeSource

    @Binds
    @Singleton
    abstract fun bindUuidGenerator(implementation: UuidV7Generator): UuidGenerator
}
