package rs.termin.app.core.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import rs.termin.app.MainActivity
import rs.termin.app.R
import javax.inject.Inject
import javax.inject.Singleton

enum class Channel(val id: String) { Appointments("appointments"), Reminders("reminders"), Waitlist("waitlist") }

/** Prikaz obaveštenja na telefonu (lokalni podsetnici i push poruke). */
@Singleton
class Notifier @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(Channel.Appointments.id, context.getString(R.string.channel_appointments), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_appointments_description) },
                NotificationChannel(Channel.Reminders.id, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_reminders_description) },
                NotificationChannel(Channel.Waitlist.id, context.getString(R.string.channel_waitlist), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_waitlist_description) },
            ),
        )
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** [key] određuje id obaveštenja: lokalni podsetnik i push za isti termin se zamenjuju, ne dupliraju. */
    fun show(channel: Channel, key: String, title: String, body: String, deepLink: Uri?) {
        if (!canNotify()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = deepLink
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(if (channel == Channel.Reminders) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_EVENT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(key, key.hashCode(), notification)
        } catch (e: SecurityException) {
            // Korisnik je u međuvremenu povukao dozvolu za obaveštenja.
            return
        }
    }

    fun cancel(key: String) {
        NotificationManagerCompat.from(context).cancel(key, key.hashCode())
    }
}

/** Deep linkovi unutar aplikacije. */
object DeepLinks {
    const val SCHEME = "termin"
    fun appointment(id: String): Uri = Uri.parse("$SCHEME://appointment/$id")
    fun salon(id: String): Uri = Uri.parse("$SCHEME://salon/$id")
    fun book(salonId: String, date: String?, serviceIds: List<String>, stylistId: String?): Uri =
        Uri.Builder().scheme(SCHEME).authority("book").appendPath(salonId).apply {
            if (date != null) appendQueryParameter("date", date)
            if (serviceIds.isNotEmpty()) appendQueryParameter("services", serviceIds.joinToString(","))
            if (stylistId != null) appendQueryParameter("stylist", stylistId)
        }.build()
    fun inbox(): Uri = Uri.parse("$SCHEME://inbox")
}
