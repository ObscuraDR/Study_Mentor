package com.elenglish.studymentor.data.session

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Access token holder.
 *
 * The access token lives **in process memory only**. It is never written to
 * DataStore, Room, SharedPreferences or a log, so it cannot outlive the process
 * or be recovered from device storage.
 */
@Singleton
class AccessTokenHolder @Inject constructor() {

    private val token = AtomicReference<String?>(null)

    fun get(): String? = token.get()

    fun set(value: String) {
        require(value.isNotBlank()) { "access token must not be blank" }
        token.set(value)
    }

    fun clear() {
        token.set(null)
    }
}

/**
 * Persistent refresh-token storage.
 *
 * Backed by [EncryptedSharedPreferences] with an AES256-GCM master key held in
 * the Android Keystore, as required by the contract for the Android client.
 */
interface RefreshTokenStorage {
    fun read(): String?
    fun write(token: String)
    fun clear()
}

/**
 * Constructed by `NetworkModule` rather than by constructor injection, so the
 * qualified encrypted [SharedPreferences] cannot be confused with an ordinary
 * preferences file.
 */
class KeystoreRefreshTokenStorage(
    private val preferences: SharedPreferences,
) : RefreshTokenStorage {

    override fun read(): String? = preferences.getString(KEY_REFRESH_TOKEN, null)

    /**
     * `commit()` is deliberate here, not an oversight.
     *
     * On write, the rotated token must be durable before the old one is
     * discarded server-side; on clear, the token must be gone before sign-out
     * is reported as done. `apply()` is asynchronous, so a process death in
     * between would either strand an unusable session or leave a live refresh
     * token on a device the user believes they signed out of.
     */
    @SuppressLint("ApplySharedPref")
    override fun write(token: String) {
        require(token.isNotBlank()) { "refresh token must not be blank" }
        preferences.edit().putString(KEY_REFRESH_TOKEN, token).commit()
    }

    @SuppressLint("ApplySharedPref")
    override fun clear() {
        preferences.edit().remove(KEY_REFRESH_TOKEN).commit()
    }

    companion object {
        const val PREFERENCES_NAME = "study_mentor_secure_session"
        private const val KEY_REFRESH_TOKEN = "refresh_token"

        /**
         * Builds the Keystore-backed preferences file.
         *
         * A corrupt or undecryptable store (for example after a device restore,
         * where the Keystore key no longer exists) is deleted and recreated. The
         * user is then simply signed out — which is correct, because an
         * unreadable refresh token can never be used again anyway.
         */
        fun createEncryptedPreferences(context: Context): SharedPreferences {
            fun build(): SharedPreferences {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    context,
                    PREFERENCES_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }

            return try {
                build()
            } catch (e: GeneralSecurityException) {
                recreate(context, ::build)
            } catch (e: IOException) {
                recreate(context, ::build)
            }
        }

        private fun recreate(
            context: Context,
            build: () -> SharedPreferences,
        ): SharedPreferences {
            context.deleteSharedPreferences(PREFERENCES_NAME)
            return build()
        }
    }
}
