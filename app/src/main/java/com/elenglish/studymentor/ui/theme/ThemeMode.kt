package com.elenglish.studymentor.ui.theme

/** Device-local theme preference. Persisted in DataStore, never synchronized. */
enum class ThemeMode(val storageValue: String) {
    Light("light"),
    Dark("dark"),
    System("system"),
    ;

    companion object {
        /** Unknown or missing stored values fall back to [System]. */
        fun fromStorageValue(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: System
    }
}
