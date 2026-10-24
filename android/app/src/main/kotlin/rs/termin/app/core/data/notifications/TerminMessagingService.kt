package rs.termin.app.core.data.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import rs.termin.app.core.common.ApplicationScope
import rs.termin.app.core.domain.repository.ReminderScheduler
import rs.termin.app.core.domain.repository.UserRepository
import javax.inject.Inject

/** Push obaveštenja (FCM). U demo režimu emulator ne šalje push; tada rade lokalni podsetnici. */
@AndroidEntryPoint
class TerminMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notifier: Notifier

    @Inject lateinit var shown: ShownReminders

    @Inject lateinit var reminders: ReminderScheduler

    @Inject lateinit var users: UserRepository

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    /** FCM registracija preko Firebase Installation ID-a: FID se čuva u profilu korisnika. */
    override fun onRegistered(installationId: String) {
        scope.launch { runCatching { users.savePushId(installationId) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"].orEmpty()
        val appointmentId = data["appointmentId"]
        val title = message.notification?.title ?: data["title"].orEmpty()
        val body = message.notification?.body ?: data["body"].orEmpty()

        // Termin otkazan ili promenjen od strane salona: lokalni podsetnici za njega više ne važe.
        if (appointmentId != null && (type == "booking_canceled_by_salon" || type == "booking_changed")) {
            reminders.cancel(appointmentId)
        }
        val reminderKind = when (type) {
            "reminder_24h" -> ReminderKind.Day
            "reminder_2h" -> ReminderKind.TwoHours
            else -> null
        }
        val key = if (reminderKind != null && appointmentId != null) {
            reminderKey(appointmentId, reminderKind)
        } else {
            "$type:${appointmentId ?: data["inboxId"].orEmpty()}"
        }
        if (reminderKind != null && shown.wasShown(key)) return

        val channel = when (type) {
            "reminder_24h", "reminder_2h", "review_request" -> Channel.Reminders
            "waitlist_slot" -> Channel.Waitlist
            else -> Channel.Appointments
        }
        val link = when {
            type == "waitlist_slot" && data["salonId"] != null ->
                DeepLinks.book(data.getValue("salonId"), data["dateKey"], data["serviceIds"]?.split(',').orEmpty(), data["stylistId"])
            appointmentId != null -> DeepLinks.appointment(appointmentId)
            else -> DeepLinks.inbox()
        }
        notifier.show(channel, key, title, body, link)
        if (reminderKind != null) shown.markShown(key)
    }
}
