package com.elenglish.studymentor.testing

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Waits for real work to finish.
 *
 * These tests drive ViewModels against a real MockWebServer, so the response
 * arrives on an OkHttp thread that `runTest`'s virtual clock knows nothing
 * about — `advanceUntilIdle()` would return before the call completed. Polling
 * against the wall clock is what actually reflects the work being done.
 */
/**
 * Lets in-flight ViewModel work finish before a test tears its dispatcher down.
 *
 * A `ViewModel` launched in `init` may still be suspended on a network call when
 * the test body ends. Calling `Dispatchers.resetMain()` while that is pending
 * leaves the coroutine unable to resume onto Main, and it throws on a background
 * thread — surfacing later as "uncaught exceptions before the test started" in
 * whichever unrelated test happens to run next.
 *
 * Shutting the server down first makes pending calls fail fast; this then gives
 * them a moment to unwind while Main is still installed.
 */
fun awaitQuiescence(millis: Long = 100) = runBlocking { delay(millis) }

suspend fun awaitCondition(
    timeoutMillis: Long = 5_000,
    pollMillis: Long = 5,
    describe: () -> String = { "condition" },
    condition: () -> Boolean,
) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        delay(pollMillis)
    }
    throw AssertionError("Timed out after ${timeoutMillis}ms waiting for ${describe()}")
}
