package com.elenglish.studymentor.core.time

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device wall clock, injected so tests can control it.
 *
 * Only used for local bookkeeping such as cache freshness. Anything the backend
 * owns — progress, XP, streaks — is timestamped by the backend, never here.
 */
interface TimeSource {
    fun nowEpochMillis(): Long
}

@Singleton
class SystemTimeSource @Inject constructor() : TimeSource {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
