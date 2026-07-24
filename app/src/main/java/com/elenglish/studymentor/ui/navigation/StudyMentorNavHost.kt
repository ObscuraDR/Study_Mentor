package com.elenglish.studymentor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.elenglish.studymentor.ui.auth.AuthScreen
import com.elenglish.studymentor.ui.shell.HomeShellScreen

/**
 * The single navigation graph of the application.
 *
 * There is no XML/Fragment destination and no legacy Activity reachable from
 * the launcher path.
 */
@Composable
fun StudyMentorNavHost(
    navController: NavHostController,
    startGraph: StudyMentorGraph,
    modifier: Modifier = Modifier,
    // Destination content is injectable so navigation can be tested without
    // standing up the Hilt graph each screen's ViewModel needs.
    guestContent: @Composable () -> Unit = { AuthScreen() },
    authenticatedContent: @Composable () -> Unit = { HomeShellScreen() },
) {
    NavHost(
        navController = navController,
        startDestination = startGraph.route,
        modifier = modifier,
    ) {
        navigation(
            route = StudyMentorGraph.Guest.route,
            startDestination = StudyMentorDestinations.GUEST_WELCOME,
        ) {
            composable(StudyMentorDestinations.GUEST_WELCOME) {
                guestContent()
            }
        }

        navigation(
            route = StudyMentorGraph.Authenticated.route,
            startDestination = StudyMentorDestinations.HOME_SHELL,
        ) {
            composable(StudyMentorDestinations.HOME_SHELL) {
                authenticatedContent()
            }
        }
    }
}
