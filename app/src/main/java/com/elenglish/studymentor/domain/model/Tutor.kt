package com.elenglish.studymentor.domain.model

/** How the provider finished, as reported by the backend. */
enum class TutorAnswerStatus(val wireValue: String) {
    /** A full answer. */
    Completed("completed"),

    /** The provider stopped early; the answer is incomplete. */
    Truncated("truncated"),

    /** A safety filter declined to answer. This is not an error. */
    Refused("refused"),
    ;

    companion object {
        /**
         * Unknown values map to [Refused] rather than [Completed].
         *
         * If a future backend adds a status this client version does not know,
         * the safe reading is "do not present this as a complete answer".
         */
        fun fromWire(value: String?): TutorAnswerStatus =
            entries.firstOrNull { it.wireValue == value } ?: Refused
    }
}

/** One tutor answer. */
data class TutorAnswer(
    val responseId: String,
    val lessonId: String,
    val answer: String,
    val createdAt: String,
    val status: TutorAnswerStatus,
    /** True when the backend replayed a stored response (HTTP 200). */
    val wasReplay: Boolean,
)

/**
 * A question awaiting an answer.
 *
 * The key is fixed when the learner sends, so a retry is recognised as the same
 * question and replays the original answer instead of spending another provider
 * call.
 */
data class PendingTutorMessage(
    val idempotencyKey: String,
    val lessonId: String,
    val message: String,
)

/** One turn in the on-screen conversation. */
sealed interface TutorTurn {
    val id: String

    data class Question(override val id: String, val text: String) : TutorTurn

    data class Answer(override val id: String, val answer: TutorAnswer) : TutorTurn
}
