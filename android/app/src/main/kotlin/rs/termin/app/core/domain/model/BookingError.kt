package rs.termin.app.core.domain.model

import java.time.Instant

/** Greške koje server vraća kao kodove; aplikacija ih prikazuje kao jasne poruke na srpskom. */
sealed class BookingError : Exception() {
    /** Neko je upravo zauzeo slot; [slots] su osveženi slotovi za taj dan. */
    data class SlotTaken(val slots: List<Slot>) : BookingError()

    data class PolicyViolation(val reason: PolicyReason, val salonPhone: String?, val deadline: Instant?) : BookingError()

    data object NotAllowed : BookingError()

    data object Validation : BookingError()

    data object NotFound : BookingError()

    data object RateLimited : BookingError()

    data object IntegrationNotConfigured : BookingError()

    /** Nema interneta (ili server nije dostupan). */
    data object Offline : BookingError()

    data class Unknown(override val message: String?) : BookingError()
}

enum class PolicyReason {
    CancelDeadline,
    OutsideWindow,
    MaxHolds,
    MaxWaitlist,
    NotActive,
    NotCompleted,
    AlreadyReviewed,
    NotStarted,
    HoldExpired,
    AlreadyInvited,
    NotConnected,
    Other,
    ;

    companion object {
        fun from(key: String?): PolicyReason = when (key) {
            "CANCEL_DEADLINE" -> CancelDeadline
            "OUTSIDE_WINDOW" -> OutsideWindow
            "MAX_HOLDS" -> MaxHolds
            "MAX_WAITLIST" -> MaxWaitlist
            "NOT_ACTIVE" -> NotActive
            "NOT_COMPLETED" -> NotCompleted
            "ALREADY_REVIEWED" -> AlreadyReviewed
            "NOT_STARTED" -> NotStarted
            "HOLD_EXPIRED" -> HoldExpired
            "ALREADY_INVITED" -> AlreadyInvited
            "NOT_CONNECTED" -> NotConnected
            else -> Other
        }
    }
}

/** Greške pri prijavi. */
sealed class AuthError : Exception() {
    data object InvalidPhone : AuthError()
    data object InvalidCode : AuthError()
    data object CodeExpired : AuthError()
    data object TooManyRequests : AuthError()
    data object Network : AuthError()
    data object GoogleUnavailable : AuthError()
    data object Cancelled : AuthError()
    data class Unknown(override val message: String?) : AuthError()
}
