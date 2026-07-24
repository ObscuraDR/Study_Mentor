package com.elenglish.studymentor.ui.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.elenglish.studymentor.R
import com.elenglish.studymentor.ui.components.StudyMentorTopAppBar
import com.elenglish.studymentor.ui.quiz.QuizAttemptContent
import com.elenglish.studymentor.ui.quiz.QuizAttemptViewModel
import com.elenglish.studymentor.ui.quiz.QuizListContent
import com.elenglish.studymentor.ui.quiz.QuizListViewModel
import com.elenglish.studymentor.ui.flashcards.FlashcardDeckListContent
import com.elenglish.studymentor.ui.flashcards.FlashcardDeckListViewModel
import com.elenglish.studymentor.ui.flashcards.FlashcardReviewContent
import com.elenglish.studymentor.ui.flashcards.FlashcardReviewViewModel
import com.elenglish.studymentor.ui.quiz.QuizUiState
import com.elenglish.studymentor.ui.tutor.TutorScreen
import com.elenglish.studymentor.ui.tutor.TutorViewModel

/**
 * Routes of the learning catalog, nested inside the authenticated shell's
 * Learn section. Every destination is reachable only from within the
 * authenticated graph, so a guest can never land on one.
 */
object CatalogRoutes {
    const val SUBJECTS = "catalog/subjects"
    const val TOPICS = "catalog/subjects/{subjectId}/topics"
    const val LESSONS = "catalog/topics/{topicId}/lessons"
    const val LESSON_DETAIL = "catalog/lessons/{lessonId}"
    const val QUIZZES = "catalog/lessons/{lessonId}/quizzes"
    const val QUIZ_ATTEMPT = "catalog/quizzes/{quizId}"
    const val TUTOR = "catalog/lessons/{lessonId}/tutor"
    const val FLASHCARD_DECKS = "catalog/lessons/{lessonId}/flashcards"
    const val FLASHCARD_REVIEW = "catalog/flashcard-decks/{deckId}/review"

    fun topics(subjectId: String) = "catalog/subjects/$subjectId/topics"
    fun lessons(topicId: String) = "catalog/topics/$topicId/lessons"
    fun lessonDetail(lessonId: String) = "catalog/lessons/$lessonId"
    fun quizzes(lessonId: String) = "catalog/lessons/$lessonId/quizzes"
    fun quizAttempt(quizId: String) = "catalog/quizzes/$quizId"
    fun tutor(lessonId: String) = "catalog/lessons/$lessonId/tutor"
    fun flashcardDecks(lessonId: String) = "catalog/lessons/$lessonId/flashcards"
    fun flashcardReview(deckId: String) = "catalog/flashcard-decks/$deckId/review"
}

/**
 * Subjects → Topics → Lessons → Lesson detail.
 *
 * Each level's title comes from the backend record for that level, so the app
 * bar never invents a name the server did not send.
 *
 * @param pendingDestination when non-null, navigated to once after this graph
 *  mounts, then reported back through [onPendingDestinationHandled] so the
 *  caller can clear it. Lets another section (Home's "Continue learning" and
 *  "Review flashcards" actions) deep-link straight to a lesson or a flashcard
 *  review here without this graph knowing anything about Home. Defaulting to
 *  `null` leaves every existing caller's behaviour unchanged: nothing new
 *  happens unless a caller opts in.
 */
@Composable
fun CatalogNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    pendingDestination: String? = null,
    onPendingDestinationHandled: () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = CatalogRoutes.SUBJECTS,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(CatalogRoutes.SUBJECTS) {
            val viewModel: SubjectsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            CatalogScaffold(title = stringResource(R.string.catalog_subjects_title)) {
                CatalogListScreen(
                    state = state,
                    listTestTag = CatalogTestTags.SUBJECTS_LIST,
                    contextLine = stringResource(R.string.catalog_subjects_context),
                    emptyTitle = stringResource(R.string.catalog_subjects_empty_title),
                    emptyDescription = stringResource(R.string.catalog_subjects_empty_body),
                    onRetry = viewModel::load,
                    key = { it.id },
                ) { subject ->
                    SubjectRow(subject) {
                        navController.navigate(CatalogRoutes.topics(subject.id))
                    }
                }
            }
        }

        composable(
            route = CatalogRoutes.TOPICS,
            arguments = listOf(
                navArgument(TopicsViewModel.ARG_SUBJECT_ID) { type = NavType.StringType },
            ),
        ) {
            val viewModel: TopicsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val subjectName by viewModel.subjectName.collectAsStateWithLifecycle()

            CatalogScaffold(
                title = subjectName ?: stringResource(R.string.catalog_topics_title),
                onNavigateBack = navController::popBackStack,
            ) {
                CatalogListScreen(
                    state = state,
                    listTestTag = CatalogTestTags.TOPICS_LIST,
                    contextLine = stringResource(R.string.catalog_topics_context),
                    emptyTitle = stringResource(R.string.catalog_topics_empty_title),
                    emptyDescription = stringResource(R.string.catalog_topics_empty_body),
                    onRetry = viewModel::load,
                    key = { it.id },
                ) { topic ->
                    TopicRow(topic) {
                        navController.navigate(CatalogRoutes.lessons(topic.id))
                    }
                }
            }
        }

        composable(
            route = CatalogRoutes.LESSONS,
            arguments = listOf(
                navArgument(LessonsViewModel.ARG_TOPIC_ID) { type = NavType.StringType },
            ),
        ) {
            val viewModel: LessonsViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val topicName by viewModel.topicName.collectAsStateWithLifecycle()
            val completedLessonIds by viewModel.completedLessonIds.collectAsStateWithLifecycle()

            CatalogScaffold(
                title = topicName ?: stringResource(R.string.catalog_lessons_title),
                onNavigateBack = navController::popBackStack,
            ) {
                CatalogListScreen(
                    state = state,
                    listTestTag = CatalogTestTags.LESSONS_LIST,
                    contextLine = stringResource(R.string.catalog_lessons_context),
                    emptyTitle = stringResource(R.string.catalog_lessons_empty_title),
                    emptyDescription = stringResource(R.string.catalog_lessons_empty_body),
                    onRetry = viewModel::load,
                    key = { it.id },
                ) { lesson ->
                    LessonRow(lesson, isCompleted = lesson.id in completedLessonIds) {
                        navController.navigate(CatalogRoutes.lessonDetail(lesson.id))
                    }
                }
            }
        }

        composable(
            route = CatalogRoutes.LESSON_DETAIL,
            arguments = listOf(
                navArgument(LessonDetailViewModel.ARG_LESSON_ID) { type = NavType.StringType },
            ),
        ) {
            val viewModel: LessonDetailViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val lessonId = it.arguments
                ?.getString(LessonDetailViewModel.ARG_LESSON_ID)
                .orEmpty()

            // The lesson's own title is the heading inside the content; repeating
            // it in the app bar would make a screen reader announce it twice.
            CatalogScaffold(
                title = stringResource(R.string.catalog_lesson_title),
                onNavigateBack = navController::popBackStack,
            ) {
                LessonDetailContent(
                    state = state,
                    onRetry = viewModel::load,
                    onMarkComplete = { viewModel.markComplete() },
                    onOpenQuizzes = {
                        navController.navigate(CatalogRoutes.quizzes(lessonId))
                    },
                    onOpenTutor = {
                        navController.navigate(CatalogRoutes.tutor(lessonId))
                    },
                    onOpenFlashcards = {
                        navController.navigate(CatalogRoutes.flashcardDecks(lessonId))
                    },
                )
            }
        }

        composable(
            route = CatalogRoutes.QUIZZES,
            arguments = listOf(
                navArgument(QuizListViewModel.ARG_LESSON_ID) { type = NavType.StringType },
            ),
        ) {
            val viewModel: QuizListViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            CatalogScaffold(
                title = stringResource(R.string.quiz_list_title),
                onNavigateBack = navController::popBackStack,
            ) {
                QuizListContent(
                    state = state,
                    onOpenQuiz = { navController.navigate(CatalogRoutes.quizAttempt(it.id)) },
                    onRetry = viewModel::load,
                )
            }
        }

        composable(
            route = CatalogRoutes.TUTOR,
            arguments = listOf(
                navArgument(TutorViewModel.ARG_LESSON_ID) { type = NavType.StringType },
            ),
        ) {
            CatalogScaffold(
                title = stringResource(R.string.tutor_title),
                onNavigateBack = navController::popBackStack,
            ) {
                TutorScreen()
            }
        }

        composable(
            route = CatalogRoutes.FLASHCARD_DECKS,
            arguments = listOf(
                navArgument(FlashcardDeckListViewModel.ARG_LESSON_ID) { type = NavType.StringType },
            ),
        ) {
            val viewModel: FlashcardDeckListViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            CatalogScaffold(
                title = stringResource(R.string.flashcards_title),
                onNavigateBack = navController::popBackStack,
            ) {
                FlashcardDeckListContent(
                    state = state,
                    onOpenDeck = { navController.navigate(CatalogRoutes.flashcardReview(it)) },
                    onRetry = viewModel::load,
                )
            }
        }

        composable(
            route = CatalogRoutes.FLASHCARD_REVIEW,
            arguments = listOf(
                navArgument(FlashcardReviewViewModel.ARG_DECK_ID) { type = NavType.StringType },
            ),
        ) {
            val viewModel: FlashcardReviewViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            CatalogScaffold(
                title = stringResource(R.string.flashcards_review_title),
                onNavigateBack = navController::popBackStack,
            ) {
                FlashcardReviewContent(
                    state = state,
                    onReveal = viewModel::reveal,
                    onAnswer = viewModel::answer,
                    onRetryLoad = viewModel::load,
                )
            }
        }

        composable(
            route = CatalogRoutes.QUIZ_ATTEMPT,
            arguments = listOf(
                navArgument(QuizAttemptViewModel.ARG_QUIZ_ID) { type = NavType.StringType },
            ),
        ) {
            val viewModel: QuizAttemptViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            CatalogScaffold(
                title = (state as? QuizUiState.Content)?.quiz?.title
                    ?: stringResource(R.string.quiz_attempt_title),
                onNavigateBack = navController::popBackStack,
            ) {
                QuizAttemptContent(
                    state = state,
                    onSelectOption = viewModel::selectOption,
                    onSubmit = viewModel::submit,
                    onRetryLoad = viewModel::load,
                )
            }
        }
    }

    LaunchedEffect(pendingDestination) {
        val destination = pendingDestination ?: return@LaunchedEffect
        navController.navigate(destination)
        onPendingDestinationHandled()
    }
}

@Composable
private fun CatalogScaffold(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        StudyMentorTopAppBar(title = title, onNavigateBack = onNavigateBack)
        content()
    }
}
