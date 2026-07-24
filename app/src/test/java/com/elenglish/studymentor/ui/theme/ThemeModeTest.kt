package com.elenglish.studymentor.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `known storage values round trip`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStorageValue(mode.storageValue))
        }
    }

    @Test
    fun `missing preference falls back to system`() {
        assertEquals(ThemeMode.System, ThemeMode.fromStorageValue(null))
    }

    @Test
    fun `unrecognised preference falls back to system`() {
        assertEquals(ThemeMode.System, ThemeMode.fromStorageValue("solarized"))
    }
}
