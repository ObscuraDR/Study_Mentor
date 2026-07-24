package com.elenglish.studymentor.ui.navigation

/**
 * Navigation graph roots. The guest graph is reachable without a session; the
 * authenticated graph is only entered once the backend has issued one.
 */
enum class StudyMentorGraph(val route: String) {
    Guest("graph/guest"),
    Authenticated("graph/authenticated"),
}

/**
 * Destination routes. Phase 2 defines the shell and its top-level sections;
 * each feature's real content is added by its own phase.
 */
object StudyMentorDestinations {
    const val GUEST_WELCOME = "guest/welcome"
    const val HOME_SHELL = "authenticated/shell"
}

/**
 * Top-level sections of the authenticated shell, mirroring the Web reference's
 * information architecture (`EL_English_Web/src/features`).
 */
enum class HomeSection(
    val route: String,
    /**
     * True when the section hosts its own navigation and renders its own app
     * bar, so the shell must not add a second one on top.
     */
    val providesOwnTopBar: Boolean = false,
) {
    Home("shell/home"),
    Learn("shell/learn", providesOwnTopBar = true),
    Progress("shell/progress"),
    Tutor("shell/tutor"),
    Profile("shell/profile"),
}

/** Test tags used by Compose UI tests. */
object StudyMentorTestTags {
    const val GUEST_WELCOME_SCREEN = "guest_welcome_screen"
    const val HOME_SCREEN = "home_screen"
    const val HOME_BOTTOM_BAR = "home_bottom_bar"
    const val HOME_TOP_BAR = "home_top_bar"

    fun sectionTab(section: HomeSection): String = "tab_${section.route}"
    fun sectionContent(section: HomeSection): String = "section_${section.route}"
}
