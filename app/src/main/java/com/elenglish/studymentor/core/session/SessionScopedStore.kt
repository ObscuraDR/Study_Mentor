package com.elenglish.studymentor.core.session

/**
 * A local store holding data that belongs to the signed-in user.
 *
 * Everything implementing this is wiped when a session ends, so one user's
 * cached content can never be shown to whoever signs in next on the device.
 */
interface SessionScopedStore {
    suspend fun clearForSignOut()
}
