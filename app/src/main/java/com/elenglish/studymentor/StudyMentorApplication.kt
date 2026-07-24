package com.elenglish.studymentor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application root. Owns the Hilt dependency graph for the rebuilt Compose app.
 */
@HiltAndroidApp
class StudyMentorApplication : Application()
