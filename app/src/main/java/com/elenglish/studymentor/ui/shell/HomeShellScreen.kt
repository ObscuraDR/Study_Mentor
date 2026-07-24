package com.elenglish.studymentor.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.elenglish.studymentor.R
import com.elenglish.studymentor.ui.components.BottomNavItem
import com.elenglish.studymentor.ui.components.EmptyState
import com.elenglish.studymentor.ui.components.StudyMentorBottomBar
import com.elenglish.studymentor.ui.components.StudyMentorSnackbarHost
import com.elenglish.studymentor.ui.components.StudyMentorTopAppBar
import com.elenglish.studymentor.ui.catalog.CatalogNavGraph
import com.elenglish.studymentor.ui.catalog.CatalogRoutes
import com.elenglish.studymentor.ui.home.HomeScreen
import com.elenglish.studymentor.ui.navigation.HomeSection
import com.elenglish.studymentor.ui.profile.ProfileScreen
import com.elenglish.studymentor.ui.progress.ProgressScreen
import com.elenglish.studymentor.ui.navigation.StudyMentorTestTags
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import com.elenglish.studymentor.ui.theme.ThemeMode

/**
 * Authenticated app shell: top app bar, bottom navigation and the selected
 * section's content.
 *
 * Home, Learn, Progress and Profile are real, backend-derived content. Tutor
 * states plainly where it lives instead of offering a lesson-less chat the
 * contract does not support. No section fabricates XP, streak, level, rank or
 * any other figure the backend did not send.
 */
@Composable
fun HomeShellScreen(
    modifier: Modifier = Modifier,
    // Injectable so the shell's navigation can be tested without the Hilt graph
    // that ProfileScreen's, HomeScreen's and the catalog's ViewModels require.
    profileContent: @Composable () -> Unit = { ProfileScreen() },
    learnContent: @Composable (
        pendingDestination: String?,
        onPendingDestinationHandled: () -> Unit,
    ) -> Unit = { pendingDestination, onPendingDestinationHandled ->
        CatalogNavGraph(
            pendingDestination = pendingDestination,
            onPendingDestinationHandled = onPendingDestinationHandled,
        )
    },
    progressContent: @Composable () -> Unit = { ProgressScreen() },
    homeContent: @Composable (
        onContinueLearning: (lessonId: String) -> Unit,
        onReviewFlashcards: (deckId: String) -> Unit,
    ) -> Unit = { onContinueLearning, onReviewFlashcards ->
        HomeScreen(onContinueLearning = onContinueLearning, onReviewFlashcards = onReviewFlashcards)
    },
) {
    var selectedSection by rememberSaveable { mutableStateOf(HomeSection.Home) }
    // Set by Home's "Continue learning" / "Review flashcards" actions so the
    // Learn section opens straight to that destination instead of its own
    // start screen. Cleared once the catalog graph has consumed it.
    var pendingCatalogDestination by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val items = homeNavItems()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(StudyMentorTestTags.HOME_SCREEN),
        topBar = {
            // Sections that host their own navigation supply their own app bar,
            // with the right title and a back affordance. Adding the shell's bar
            // on top would stack two headers and show a stale section title.
            if (!selectedSection.providesOwnTopBar) {
                StudyMentorTopAppBar(
                    title = sectionTitle(selectedSection),
                    modifier = Modifier.testTag(StudyMentorTestTags.HOME_TOP_BAR),
                )
            }
        },
        bottomBar = {
            StudyMentorBottomBar(
                items = items,
                selectedRoute = selectedSection.route,
                onSelect = { item ->
                    selectedSection = HomeSection.entries.first { it.route == item.route }
                },
                modifier = Modifier.testTag(StudyMentorTestTags.HOME_BOTTOM_BAR),
            )
        },
        snackbarHost = { StudyMentorSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HomeSectionContent(
                section = selectedSection,
                profileContent = profileContent,
                learnContent = {
                    learnContent(pendingCatalogDestination) { pendingCatalogDestination = null }
                },
                progressContent = progressContent,
                homeContent = {
                    homeContent(
                        { lessonId ->
                            pendingCatalogDestination = CatalogRoutes.lessonDetail(lessonId)
                            selectedSection = HomeSection.Learn
                        },
                        { deckId ->
                            pendingCatalogDestination = CatalogRoutes.flashcardReview(deckId)
                            selectedSection = HomeSection.Learn
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun HomeSectionContent(
    section: HomeSection,
    profileContent: @Composable () -> Unit,
    learnContent: @Composable () -> Unit,
    progressContent: @Composable () -> Unit,
    homeContent: @Composable () -> Unit,
) {
    Box(modifier = Modifier.testTag(StudyMentorTestTags.sectionContent(section))) {
        // Exhaustive on purpose: a section added to HomeSection in the future
        // must be given a real branch here before it will compile. That is
        // what the Home tab was missing — it fell through a catch-all `else`
        // to the generic "not connected" placeholder from Phase 2 onward, long
        // after Phase 4/5/7 gave it real data to show.
        when (section) {
            // Home renders a real dashboard built from data other screens
            // already fetch: the backend's progress projection, the first
            // lesson in catalog order, and that lesson's due flashcards.
            HomeSection.Home -> homeContent()

            // Profile is backed by the real account contract from Phase 3.
            HomeSection.Profile -> profileContent()

            // Learn is backed by the real learning catalog from Phase 4.
            HomeSection.Learn -> learnContent()

            // Progress renders the backend's own projection from Phase 5.
            HomeSection.Progress -> progressContent()

            // The tutor endpoint requires a lessonId, so there is no lesson-less
            // tutor to show here. Say where it lives rather than inventing a
            // general-purpose chat the contract does not support.
            HomeSection.Tutor -> EmptyState(
                title = sectionTitle(section),
                description = stringResource(R.string.tutor_section_body),
                icon = sectionIcon(section),
            )
        }
    }
}

@Composable
private fun homeNavItems(): List<BottomNavItem> = HomeSection.entries.map { section ->
    BottomNavItem(
        route = section.route,
        label = sectionTitle(section),
        icon = sectionIcon(section),
    )
}

@Composable
private fun sectionTitle(section: HomeSection): String = stringResource(
    when (section) {
        HomeSection.Home -> R.string.section_home
        HomeSection.Learn -> R.string.section_learn
        HomeSection.Progress -> R.string.section_progress
        HomeSection.Tutor -> R.string.section_tutor
        HomeSection.Profile -> R.string.section_profile
    },
)

private fun sectionIcon(section: HomeSection) = when (section) {
    HomeSection.Home -> Icons.Filled.Home
    HomeSection.Learn -> Icons.AutoMirrored.Filled.MenuBook
    HomeSection.Progress -> Icons.AutoMirrored.Filled.TrendingUp
    HomeSection.Tutor -> Icons.AutoMirrored.Filled.Chat
    HomeSection.Profile -> Icons.Filled.Person
}

@Preview(showBackground = true, name = "Shell — light")
@Composable
private fun HomeShellScreenLightPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Light) { HomeShellScreen() }
}

@Preview(showBackground = true, name = "Shell — dark")
@Composable
private fun HomeShellScreenDarkPreview() {
    StudyMentorTheme(themeMode = ThemeMode.Dark) { HomeShellScreen() }
}
