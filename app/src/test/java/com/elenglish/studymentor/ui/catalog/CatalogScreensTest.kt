package com.elenglish.studymentor.ui.catalog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.elenglish.studymentor.domain.model.CompletionResult
import com.elenglish.studymentor.domain.model.DataOrigin
import com.elenglish.studymentor.domain.model.Difficulty
import com.elenglish.studymentor.domain.model.Lesson
import com.elenglish.studymentor.domain.model.LearningEvent
import com.elenglish.studymentor.domain.model.ProgressProjection
import com.elenglish.studymentor.domain.model.Subject
import com.elenglish.studymentor.ui.theme.StudyMentorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CatalogScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun lesson(
        id: String = "lesson-1",
        title: String = "Present simple",
        description: String = "When to use it.",
        minutes: Int = 12,
        difficulty: Difficulty? = Difficulty.Beginner,
    ) = Lesson(id, "topic-1", "slug-$id", title, description, minutes, difficulty, 0)

    private fun completionResult(xpEarned: Int = 10, wasReplay: Boolean = false) = CompletionResult(
        event = LearningEvent("event-1", "lesson-1", "2026-07-24T08:00:00Z", xpEarned, 600, "2026-07-24T08:00:01Z"),
        progress = ProgressProjection(1, 3, 0, 2, 0, 1, xpEarned, 600, 33.33),
        wasReplay = wasReplay,
    )

    // -- CatalogListScreen: rendering, ordering, states ------------------------

    @Test
    fun `subjects render as cards showing their name`() {
        composeRule.setContent {
            StudyMentorTheme {
                CatalogListScreen(
                    state = CatalogUiState.Content(
                        items = listOf(Subject("s-1", "grammar", "Grammar", 0)),
                        origin = DataOrigin.Live,
                        cachedAtEpochMillis = null,
                    ),
                    listTestTag = "list",
                    contextLine = "Choose a subject to start learning.",
                    emptyTitle = "empty",
                    emptyDescription = "empty body",
                    onRetry = {},
                    key = { it.id },
                ) { subject -> SubjectRow(subject) {} }
            }
        }

        composeRule.onNodeWithText("Grammar").assertIsDisplayed()
    }

    @Test
    fun `subject rows render in exactly the order given, not resorted`() {
        composeRule.setContent {
            StudyMentorTheme {
                CatalogListScreen(
                    state = CatalogUiState.Content(
                        items = listOf(
                            Subject("s-1", "zeta", "Zeta", 0),
                            Subject("s-2", "alpha", "Alpha", 1),
                        ),
                        origin = DataOrigin.Live,
                        cachedAtEpochMillis = null,
                    ),
                    listTestTag = "list",
                    contextLine = "context",
                    emptyTitle = "empty",
                    emptyDescription = "empty body",
                    onRetry = {},
                    key = { it.id },
                ) { subject -> SubjectRow(subject) {} }
            }
        }

        // "Zeta" was given first even though it is not first alphabetically —
        // asserting its top is above "Alpha"'s proves the list renders
        // exactly the order it was given, never re-sorted for display.
        val zetaTop = composeRule.onNodeWithText("Zeta").fetchSemanticsNode().boundsInRoot.top
        val alphaTop = composeRule.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.top
        assertTrue(zetaTop < alphaTop)
    }

    @Test
    fun `a loading catalog list shows the loading state`() {
        composeRule.setContent {
            StudyMentorTheme {
                CatalogListScreen<Subject>(
                    state = CatalogUiState.Loading,
                    listTestTag = "list",
                    contextLine = "context",
                    emptyTitle = "empty",
                    emptyDescription = "empty body",
                    onRetry = {},
                    key = { it.id },
                ) { }
            }
        }

        composeRule.onNodeWithText("Loading").assertIsDisplayed()
    }

    @Test
    fun `an empty catalog list shows the empty state, not an error`() {
        composeRule.setContent {
            StudyMentorTheme {
                CatalogListScreen<Subject>(
                    state = CatalogUiState.Empty(DataOrigin.Live),
                    listTestTag = "list",
                    contextLine = "context",
                    emptyTitle = "No subjects yet",
                    emptyDescription = "Nothing to study yet.",
                    onRetry = {},
                    key = { it.id },
                ) { }
            }
        }

        composeRule.onNodeWithText("No subjects yet").assertIsDisplayed()
    }

    @Test
    fun `a failed catalog list shows the request id and a working retry`() {
        var retried = false
        composeRule.setContent {
            StudyMentorTheme {
                CatalogListScreen<Subject>(
                    state = CatalogUiState.Failed(CatalogErrorKind.Network, requestId = "req-catalog-1"),
                    listTestTag = "list",
                    contextLine = "context",
                    emptyTitle = "empty",
                    emptyDescription = "empty body",
                    onRetry = { retried = true },
                    key = { it.id },
                ) { }
            }
        }

        composeRule.onNodeWithText("Reference: req-catalog-1").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun `the context line is shown regardless of loading, empty or content state`() {
        composeRule.setContent {
            StudyMentorTheme {
                CatalogListScreen<Subject>(
                    state = CatalogUiState.Loading,
                    listTestTag = "list",
                    contextLine = "Choose a subject to start learning.",
                    emptyTitle = "empty",
                    emptyDescription = "empty body",
                    onRetry = {},
                    key = { it.id },
                ) { }
            }
        }

        composeRule.onNodeWithTag(CatalogTestTags.CONTEXT_LINE).assertIsDisplayed()
        composeRule.onNodeWithText("Choose a subject to start learning.").assertIsDisplayed()
    }

    @Test
    fun `tapping a subject row reports its id`() {
        var opened: String? = null
        composeRule.setContent {
            StudyMentorTheme {
                SubjectRow(Subject("s-9", "grammar", "Grammar", 0)) { opened = "s-9" }
            }
        }

        composeRule.onNodeWithTag(CatalogTestTags.row("s-9")).performClick()
        assertEquals("s-9", opened)
    }

    // -- Lesson rows -----------------------------------------------------------

    @Test
    fun `lesson rows show duration and difficulty as tags`() {
        composeRule.setContent {
            StudyMentorTheme {
                LessonRow(lesson(minutes = 12, difficulty = Difficulty.Beginner)) {}
            }
        }

        composeRule.onNodeWithText("12 min").assertIsDisplayed()
        composeRule.onNodeWithText("Beginner").assertIsDisplayed()
    }

    @Test
    fun `a lesson with no difficulty from the backend omits the difficulty tag`() {
        composeRule.setContent {
            StudyMentorTheme {
                LessonRow(lesson(difficulty = null)) {}
            }
        }

        composeRule.onNodeWithText("Beginner").assertDoesNotExist()
        composeRule.onNodeWithText("Intermediate").assertDoesNotExist()
        composeRule.onNodeWithText("Advanced").assertDoesNotExist()
    }

    @Test
    fun `lesson rows never show a fabricated completion mark or percentage`() {
        composeRule.setContent {
            StudyMentorTheme {
                LessonRow(lesson()) {}
            }
        }

        listOf("Completed", "Done", "%", "Streak", "Level").forEach { forbidden ->
            composeRule.onNodeWithText(forbidden, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `a lesson the backend confirms as completed shows a real completion mark`() {
        composeRule.setContent {
            StudyMentorTheme {
                LessonRow(lesson(), isCompleted = true) {}
            }
        }

        // useUnmergedNode: the icon's semantics merge into the clickable
        // card's single accessibility node, so the tag is only found in the
        // unmerged tree — expected Compose accessibility-merging behaviour,
        // not a rendering problem.
        composeRule.onNodeWithTag(CatalogTestTags.LESSON_COMPLETED_MARK, useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `a lesson not known to be completed shows no completion mark`() {
        composeRule.setContent {
            StudyMentorTheme {
                LessonRow(lesson(), isCompleted = false) {}
            }
        }

        composeRule.onNodeWithTag(CatalogTestTags.LESSON_COMPLETED_MARK, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `tapping a lesson row reports its id`() {
        var opened: String? = null
        composeRule.setContent {
            StudyMentorTheme {
                LessonRow(lesson(id = "lesson-7")) { opened = "lesson-7" }
            }
        }

        composeRule.onNodeWithTag(CatalogTestTags.row("lesson-7")).performClick()
        assertEquals("lesson-7", opened)
    }

    // -- Lesson detail: content, actions, completion ----------------------------

    private fun setLessonDetail(
        completion: CompletionState = CompletionState.Idle,
        onMarkComplete: () -> Unit = {},
        onOpenQuizzes: () -> Unit = {},
        onOpenFlashcards: () -> Unit = {},
        onOpenTutor: () -> Unit = {},
    ) {
        composeRule.setContent {
            StudyMentorTheme {
                LessonDetailContent(
                    state = LessonDetailUiState.Content(
                        lesson = lesson(),
                        origin = DataOrigin.Live,
                        completion = completion,
                    ),
                    onRetry = {},
                    onMarkComplete = onMarkComplete,
                    onOpenQuizzes = onOpenQuizzes,
                    onOpenTutor = onOpenTutor,
                    onOpenFlashcards = onOpenFlashcards,
                )
            }
        }
    }

    @Test
    fun `lesson detail shows the title, tags and description from the backend`() {
        setLessonDetail()

        composeRule.onNodeWithText("Present simple").assertIsDisplayed()
        composeRule.onNodeWithText("12 min").assertIsDisplayed()
        composeRule.onNodeWithText("Beginner").assertIsDisplayed()
        composeRule.onNodeWithText("When to use it.").assertIsDisplayed()
    }

    @Test
    fun `all three supporting actions are visible and navigate correctly`() {
        var quizzes = false
        var flashcards = false
        var tutor = false
        setLessonDetail(
            onOpenQuizzes = { quizzes = true },
            onOpenFlashcards = { flashcards = true },
            onOpenTutor = { tutor = true },
        )

        composeRule.onNodeWithTag(CatalogTestTags.OPEN_QUIZZES).assertIsDisplayed().performClick()
        assertEquals(true, quizzes)
        composeRule.onNodeWithTag(CatalogTestTags.OPEN_FLASHCARDS).assertIsDisplayed().performClick()
        assertEquals(true, flashcards)
        composeRule.onNodeWithTag(CatalogTestTags.OPEN_TUTOR).assertIsDisplayed().performClick()
        assertEquals(true, tutor)
    }

    @Test
    fun `mark as complete is offered as the idle primary action`() {
        var marked = false
        setLessonDetail(completion = CompletionState.Idle, onMarkComplete = { marked = true })

        composeRule.onNodeWithTag(CatalogTestTags.COMPLETION_ACTION).assertIsDisplayed().performClick()
        assertEquals(true, marked)
    }

    @Test
    fun `an accepted completion shows the awarded xp and the backend progress figures`() {
        setLessonDetail(completion = CompletionState.Accepted(completionResult(xpEarned = 10)))

        // Scrolled into view explicitly: with real content in every section
        // above it, this card can be laid out correctly yet sit outside the
        // Robolectric test window's default viewport — a test-harness
        // limitation, not a real off-screen bug (the live screenshots in the
        // completion report show it fully reachable by scrolling).
        composeRule.onNodeWithTag(CatalogTestTags.COMPLETION_ACCEPTED).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(CatalogTestTags.PROGRESS_FIGURES).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("10 XP awarded").assertIsDisplayed()
    }

    @Test
    fun `a lesson already completed this session does not invite a resubmit`() {
        setLessonDetail(completion = CompletionState.AlreadyCompletedThisSession)

        composeRule.onNodeWithTag(CatalogTestTags.COMPLETION_ACCEPTED).assertIsDisplayed()
        composeRule.onNodeWithTag(CatalogTestTags.COMPLETION_ACTION).assertDoesNotExist()
    }

    @Test
    fun `a lesson the backend shows completed from a previous session does not invite a resubmit`() {
        // Distinct from the session-registry case above: this comes fresh
        // from GET me lesson-completions on load, so it is what a learner
        // sees after restarting the app on a lesson they finished earlier.
        setLessonDetail(completion = CompletionState.CompletedPreviously("2026-07-20T08:00:00Z"))

        composeRule.onNodeWithTag(CatalogTestTags.COMPLETION_ACCEPTED).assertIsDisplayed()
        composeRule.onNodeWithTag(CatalogTestTags.COMPLETION_ACTION).assertDoesNotExist()
    }

    @Test
    fun `a retryable completion failure shows the request id and a retry action`() {
        var retried = false
        setLessonDetail(
            completion = CompletionState.Failed(CatalogErrorKind.Network, requestId = "req-complete-1", canRetry = true),
            onMarkComplete = { retried = true },
        )

        composeRule.onNodeWithText("Reference: req-complete-1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performScrollTo().performClick()
        assertEquals(true, retried)
    }

    @Test
    fun `a definitive completion failure offers no retry`() {
        setLessonDetail(
            completion = CompletionState.Failed(CatalogErrorKind.NotFound, requestId = null, canRetry = false),
        )

        composeRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun `a loading lesson detail shows the loading state`() {
        composeRule.setContent {
            StudyMentorTheme {
                LessonDetailContent(
                    state = LessonDetailUiState.Loading,
                    onRetry = {},
                    onMarkComplete = {},
                    onOpenQuizzes = {},
                    onOpenTutor = {},
                    onOpenFlashcards = {},
                )
            }
        }

        composeRule.onNodeWithText("Loading").assertIsDisplayed()
    }

    @Test
    fun `a failed lesson detail shows the request id and a working retry`() {
        var retried = false
        composeRule.setContent {
            StudyMentorTheme {
                LessonDetailContent(
                    state = LessonDetailUiState.Failed(CatalogErrorKind.Network, requestId = "req-lesson-1"),
                    onRetry = { retried = true },
                    onMarkComplete = {},
                    onOpenQuizzes = {},
                    onOpenTutor = {},
                    onOpenFlashcards = {},
                )
            }
        }

        composeRule.onNodeWithText("Reference: req-lesson-1").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun `no fake streak, level, rank, quest or coin figure is ever shown on lesson detail`() {
        setLessonDetail(completion = CompletionState.Accepted(completionResult()))

        listOf("Streak", "Level", "Rank", "Quest", "Coin", "Coins").forEach { forbidden ->
            composeRule.onNodeWithText(forbidden, substring = true).assertDoesNotExist()
        }
    }
}
