package rs.termin.app.core.data.notifications

import android.content.Context
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import rs.termin.app.R
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.repository.ReminderScheduler
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ReminderKind(val key: String, val before: Duration) {
    Day("h24", Duration.ofHours(24)),
    TwoHours("h2", Duration.ofHours(2)),
}

/** Pamti koje je podsetnike telefon već prikazao (lokalno ili push), da ne bi bilo duplikata. */
@Singleton
class ShownReminders @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("shown_reminders", Context.MODE_PRIVATE)

    fun wasShown(key: String): Boolean = prefs.getBoolean(key, false)

    fun markShown(key: String) {
        prefs.edit { putBoolean(key, true) }
    }
}

fun reminderKey(appointmentId: String, kind: ReminderKind): String = "reminder:$appointmentId:${kind.key}"

/**
 * Lokalni podsetnici 24 h i 2 h pre termina. Zakazuju se pri potvrdi, a otkazuju pri promeni
 * ili otkazivanju; rade i bez interneta i u demo režimu (gde push ne postoji).
 */
@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) : ReminderScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override fun schedule(appointment: Appointment) {
        if (appointment.status != AppointmentStatus.Confirmed) {
            cancel(appointment.id)
            return
        }
        val now = clock.instant()
        for (kind in ReminderKind.entries) {
            val at = appointment.start.minus(kind.before)
            val name = workName(appointment.id, kind)
            if (!at.isAfter(now)) {
                workManager.cancelUniqueWork(name)
                continue
            }
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(Duration.between(now, at).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        ReminderWorker.KEY_ID to appointment.id,
                        ReminderWorker.KEY_KIND to kind.key,
                        ReminderWorker.KEY_SALON to appointment.salonName,
                        ReminderWorker.KEY_START to appointment.start.toEpochMilli(),
                    ),
                )
                .addTag(TAG)
                .build()
            workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
        }
    }

    override fun cancel(appointmentId: String) {
        ReminderKind.entries.forEach { workManager.cancelUniqueWork(workName(appointmentId, it)) }
    }

    override fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
    }

    private fun workName(id: String, kind: ReminderKind) = "reminder-$id-${kind.key}"

    private companion object {
        const val TAG = "appointment-reminder"
    }
}

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: Notifier,
    private val shown: ShownReminders,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val kind = ReminderKind.entries.firstOrNull { it.key == inputData.getString(KEY_KIND) } ?: return Result.failure()
        val salon = inputData.getString(KEY_SALON).orEmpty()
        val start = Instant.ofEpochMilli(inputData.getLong(KEY_START, 0))
        val key = reminderKey(id, kind)
        if (shown.wasShown(key)) return Result.success()
        val time = DateTimeFormatter.ofPattern("HH:mm").format(start.atZone(BelgradeZone))
        val (title, body) = when (kind) {
            ReminderKind.Day -> applicationContext.getString(R.string.reminder_day_title) to
                applicationContext.getString(R.string.reminder_day_body, salon, time)
            ReminderKind.TwoHours -> applicationContext.getString(R.string.reminder_two_hours_title) to
                applicationContext.getString(R.string.reminder_two_hours_body, salon, time)
        }
        notifier.show(Channel.Reminders, key, title, body, DeepLinks.appointment(id))
        shown.markShown(key)
        return Result.success()
    }

    companion object {
        const val KEY_ID = "appointmentId"
        const val KEY_KIND = "kind"
        const val KEY_SALON = "salonName"
        const val KEY_START = "start"
    }
}
