package com.elenglish.studymentor.ui.theme

/** Device-local theme preference, persisted in DataStore. */
enum class ThemeMode(val storageValue: String) {
    Light("light"),
    Dark("dark"),
    System("system"),
    ;

    companion object {
        fun fromStorageValue(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: System
    }
}
