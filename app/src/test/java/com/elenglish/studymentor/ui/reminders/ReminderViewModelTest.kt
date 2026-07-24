package com.elenglish.studymentor.ui.reminders

import com.elenglish.studymentor.data.preferences.AppPreferencesRepository
import com.elenglish.studymentor.data.preferences.ReminderPreference
import com.elenglish.studymentor.notifications.ReminderScheduler
import com.elenglish.studymentor.testing.awaitCondition
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderViewModelTest {

    private val stored = MutableStateFlow(ReminderPreference())
    private val preferences: AppPreferencesRepository = mockk(relaxed = true) {
        every { reminder } returns stored
        coEvery { setReminder(any()) } answers { stored.value = firstArg() }
    }
    private val scheduler: ReminderScheduler = mockk(relaxed = true)

    private lateinit var viewModel: ReminderViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ReminderViewModel(preferences, scheduler)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun awaitEnabled(expected: Boolean) = runBlocking {
        awaitCondition(describe = { "reminder enabled = $expected" }) {
            viewModel.uiState.value.preference.enabled == expected
        }
    }

    @Test
    fun `enabling without permission requests it instead of claiming a reminder is set`() {
        viewModel.onPermissionStateChanged(granted = false)

        viewModel.onEnabledChange(true)

        assertTrue(viewModel.uiState.value.awaitingPermission)
        assertFalse(viewModel.uiState.value.preference.enabled)
        coVerify(exactly = 0) { scheduler.schedule(any(), any()) }
    }

    @Test
    fun `granting permission enables and schedules the reminder`() {
        viewModel.onPermissionStateChanged(granted = false)
        viewModel.onEnabledChange(true)

        viewModel.onPermissionResult(granted = true)
        awaitEnabled(true)

        val times = mutableListOf<LocalTime>()
        coVerify { scheduler.schedule(capture(times), any()) }
        // The default reminder time is 20:00 local.
        assertEquals(LocalTime.of(20, 0), times.last())
        assertTrue(viewModel.uiState.value.preference.enabled)
    }

    @Test
    fun `denying permission leaves reminders off`() {
        viewModel.onPermissionStateChanged(granted = false)
        viewModel.onEnabledChange(true)

        viewModel.onPermissionResult(granted = false)

        assertFalse(viewModel.uiState.value.preference.enabled)
        assertFalse(viewModel.uiState.value.awaitingPermission)
        coVerify(exactly = 0) { scheduler.schedule(any(), any()) }
    }

    @Test
    fun `disabling cancels the scheduled work`() {
        viewModel.onPermissionStateChanged(granted = true)
        viewModel.onEnabledChange(true)
        awaitEnabled(true)

        viewModel.onEnabledChange(false)
        awaitEnabled(false)

        coVerify { scheduler.cancel() }
    }

    @Test
    fun `revoking permission in system settings turns the reminder off`() {
        viewModel.onPermissionStateChanged(granted = true)
        viewModel.onEnabledChange(true)
        awaitEnabled(true)

        // The user turned notifications off outside the app; nothing could fire,
        // so the switch must not keep claiming a reminder is set.
        viewModel.onPermissionStateChanged(granted = false)
        awaitEnabled(false)

        coVerify { scheduler.cancel() }
    }

    @Test
    fun `changing the time reschedules at the new local time`() {
        viewModel.onPermissionStateChanged(granted = true)
        viewModel.onEnabledChange(true)
        awaitEnabled(true)

        viewModel.onTimeChange(hour = 7, minute = 45)
        runBlocking {
            awaitCondition(describe = { "time to update" }) {
                viewModel.uiState.value.preference.hour == 7
            }
        }

        // Capture every scheduling call; the latest must carry the new time.
        val times = mutableListOf<LocalTime>()
        coVerify { scheduler.schedule(capture(times), any()) }
        assertEquals(LocalTime.of(7, 45), times.last())
        assertTrue(viewModel.uiState.value.preference.enabled)
    }
}
