package com.elenglish.studymentor.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.elenglish.studymentor.BuildConfig
import com.elenglish.studymentor.core.network.ApiErrorCodes
import com.elenglish.studymentor.data.local.StudyMentorDatabase
import com.elenglish.studymentor.data.preferences.AppPreferencesRepository
import com.elenglish.studymentor.data.preferences.ReminderPreference
import com.elenglish.studymentor.di.NetworkModule
import com.elenglish.studymentor.ui.theme.ThemeMode
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Standing security checks.
 *
 * Each one guards a property that is easy to regress silently: a logging level
 * raised while debugging, a token parked in DataStore "just for now", a cache
 * table that starts carrying credentials.
 */
@RunWith(RobolectricTestRunner::class)
class SecurityAuditTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private var database: StudyMentorDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun `network logging never records bodies or headers`() {
        val level = NetworkModule.provideLoggingInterceptor().level

        // BODY would print access and refresh tokens and every tutor prompt;
        // HEADERS would print the Authorization header.
        assertNotEquals(HttpLoggingInterceptor.Level.BODY, level)
        assertNotEquals(HttpLoggingInterceptor.Level.HEADERS, level)
    }

    @Test
    fun `the release build points at https and disables logging`() {
        // Guards against shipping a debug host or debug logging in release.
        if (!BuildConfig.DEBUG) {
            assertTrue(
                "release must use https",
                BuildConfig.API_BASE_URL.startsWith("https://"),
            )
            assertFalse("release must not log network traffic", BuildConfig.ENABLE_NETWORK_LOGGING)
        } else {
            // The debug host is the emulator loopback alias and nothing else.
            assertTrue(
                "debug must target the local backend",
                BuildConfig.API_BASE_URL.startsWith("http://10.0.2.2:") ||
                    BuildConfig.API_BASE_URL.startsWith("https://"),
            )
        }
    }

    @Test
    fun `the local cache schema holds no credential-shaped columns`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            context,
            StudyMentorDatabase::class.java,
        ).allowMainThreadQueries().build().also { database = it }
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = OFF")

        val columns = mutableListOf<String>()
        db.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table'",
        ).use { tables ->
            while (tables.moveToNext()) {
                val table = tables.getString(0)
                if (table.startsWith("sqlite_") || table == "room_master_table") continue
                db.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { info ->
                    while (info.moveToNext()) columns += "$table.${info.getString(1)}".lowercase()
                }
            }
        }

        assertTrue("expected cache tables to exist", columns.isNotEmpty())
        listOf("token", "password", "secret", "authorization", "credential", "refresh").forEach {
            assertTrue(
                "no cache column may look like a credential, found: " +
                    columns.filter { column -> column.contains(it) },
                columns.none { column -> column.contains(it) },
            )
        }
    }

    @Test
    fun `preferences storage holds no credential-shaped keys`() = runTest {
        val file = File.createTempFile("audit", ".preferences_pb").apply { delete() }
        val store = PreferenceDataStoreFactory.create { file }
        val repository = AppPreferencesRepository(store)

        repository.setThemeMode(ThemeMode.Dark)
        repository.setReminder(ReminderPreference(enabled = true, hour = 7, minute = 30))

        val keys: Set<Preferences.Key<*>> = store.data.first().asMap().keys
        val names = keys.map { it.name.lowercase() }

        assertEquals(
            setOf("theme_mode", "reminder_enabled", "reminder_hour", "reminder_minute"),
            names.toSet(),
        )
        listOf("token", "password", "secret", "auth").forEach { forbidden ->
            assertTrue(
                "DataStore must not hold credentials, found: ${names.filter { it.contains(forbidden) }}",
                names.none { it.contains(forbidden) },
            )
        }
        file.delete()
    }

    @Test
    fun `session-ending error codes are recognised so a dead session cannot linger`() {
        // If these stopped being treated as unrecoverable, the app would keep
        // retrying with a revoked token instead of signing the user out.
        assertTrue(ApiErrorCodes.AUTH_SESSION_REVOKED in ApiErrorCodes.UNRECOVERABLE_SESSION_CODES)
        assertTrue(
            ApiErrorCodes.AUTH_REFRESH_TOKEN_INVALID in ApiErrorCodes.UNRECOVERABLE_SESSION_CODES,
        )
        assertTrue(
            ApiErrorCodes.AUTH_REFRESH_TOKEN_REUSED in ApiErrorCodes.UNRECOVERABLE_SESSION_CODES,
        )
    }
}
