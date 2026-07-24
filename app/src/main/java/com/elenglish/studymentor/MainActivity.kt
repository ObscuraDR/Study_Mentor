package com.elenglish.studymentor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.elenglish.studymentor.ui.components.LoadingState
import com.elenglish.studymentor.ui.navigation.StudyMentorGraph
import com.elenglish.studymentor.ui.navigation.StudyMentorNavHost
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single launcher entry point of the rebuilt application.
 *
 * There is no Activity/Fragment/XML screen path: every destination is Compose.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { StudyMentorApp() }
    }
}

@Composable
private fun StudyMentorApp(viewModel: AppViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    StudyMentorTheme(themeMode = state.themeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (state.sessionStatus) {
                // Session restoration is in flight. Showing the guest screen here
                // would flash a sign-in form at a user who is already signed in.
                SessionStatus.Restoring -> LoadingState()

                SessionStatus.Guest, SessionStatus.Authenticated -> {
                    val navController = rememberNavController()
                    StudyMentorNavHost(
                        navController = navController,
                        startGraph = if (state.isAuthenticated) {
                            StudyMentorGraph.Authenticated
                        } else {
                            StudyMentorGraph.Guest
                        },
                    )
                }
            }
        }
    }
}
