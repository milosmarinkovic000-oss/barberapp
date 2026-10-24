package rs.termin.app.core.data.booking

import rs.termin.app.core.data.firebase.FunctionsClient
import rs.termin.app.core.data.firebase.parseSlots
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.AppointmentSource
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.Availability
import rs.termin.app.core.domain.model.BookedService
import rs.termin.app.core.domain.model.DaySlots
import rs.termin.app.core.domain.model.Hold
import rs.termin.app.core.domain.repository.BookingRepository
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Sve odluke o zakazivanju donosi server (Cloud Functions); aplikacija samo prikazuje. */
@Singleton
class FunctionsBookingRepository @Inject constructor(private val client: FunctionsClient) : BookingRepository {

    override suspend fun availability(
        salonId: String,
        serviceIds: List<String>,
        stylistId: String?,
        fromDate: LocalDate,
        days: Int,
        excludeAppointmentId: String?,
    ): Availability {
        val result = client.callWithRetry(
            "getAvailability",
            buildMap {
                put("salonId", salonId)
                put("serviceIds", serviceIds)
                put("stylistId", stylistId)
                put("fromDate", fromDate.toString())
                put("days", days)
                if (excludeAppointmentId != null) put("excludeAppointmentId", excludeAppointmentId)
            },
        )
        return parseAvailability(result)
    }

    override suspend fun holdSlot(salonId: String, serviceIds: List<String>, stylistId: String?, start: Instant, replaceHoldId: String?): Hold {
        val result = client.call(
            "holdSlot",
            buildMap {
                put("salonId", salonId)
                put("serviceIds", serviceIds)
                put("stylistId", stylistId)
                put("start", start.toEpochMilli())
                if (replaceHoldId != null) put("replaceHoldId", replaceHoldId)
            },
        )
        return Hold(
            appointmentId = result["appointmentId"] as String,
            stylistId = result["stylistId"] as String,
            stylistName = result["stylistName"] as? String ?: "",
            start = Instant.ofEpochMilli((result["start"] as Number).toLong()),
            end = Instant.ofEpochMilli((result["end"] as Number).toLong()),
            expiresAt = Instant.ofEpochMilli((result["holdExpiresAt"] as Number).toLong()),
            durationMin = (result["durationMin"] as Number).toInt(),
            totalPrice = (result["totalPrice"] as Number).toInt(),
        )
    }

    override suspend fun releaseHold(appointmentId: String) {
        client.call("releaseHold", mapOf("appointmentId" to appointmentId))
    }

    override suspend fun confirmBooking(appointmentId: String, note: String, waitlistId: String?): Appointment {
        val result = client.call(
            "confirmBooking",
            buildMap {
                put("appointmentId", appointmentId)
                put("note", note)
                if (waitlistId != null) put("waitlistId", waitlistId)
            },
        )
        return parseAppointment(result["appointment"] as Map<*, *>)
    }

    override suspend fun cancel(appointmentId: String, reason: String) {
        client.call("cancelAppointment", mapOf("appointmentId" to appointmentId, "reason" to reason))
    }

    override suspend fun reschedule(appointmentId: String, start: Instant, stylistId: String?): Appointment {
        val result = client.call(
            "rescheduleAppointment",
            mapOf("appointmentId" to appointmentId, "start" to start.toEpochMilli(), "stylistId" to stylistId),
        )
        return parseAppointment(result["appointment"] as Map<*, *>)
    }

    override suspend fun joinWaitlist(salonId: String, date: LocalDate, serviceIds: List<String>, stylistId: String?): String {
        val result = client.call(
            "joinWaitlist",
            mapOf("salonId" to salonId, "dateKey" to date.toString(), "serviceIds" to serviceIds, "stylistId" to stylistId),
        )
        return result["waitlistId"] as String
    }

    override suspend fun leaveWaitlist(waitlistId: String) {
        client.call("leaveWaitlist", mapOf("waitlistId" to waitlistId))
    }

    override suspend fun submitReview(appointmentId: String, rating: Int, text: String) {
        client.call("submitReview", mapOf("appointmentId" to appointmentId, "rating" to rating, "text" to text))
    }
}

fun parseAvailability(result: Map<String, Any?>): Availability = Availability(
    salonId = result["salonId"] as? String ?: "",
    durationMin = (result["durationMin"] as? Number)?.toInt() ?: 0,
    totalPrice = (result["totalPrice"] as? Number)?.toInt() ?: 0,
    stylistIds = (result["stylistIds"] as? List<*>).orEmpty().filterIsInstance<String>(),
    days = (result["days"] as? List<*>).orEmpty().mapNotNull { day ->
        val m = day as? Map<*, *> ?: return@mapNotNull null
        val date = runCatching { LocalDate.parse(m["date"] as String) }.getOrNull() ?: return@mapNotNull null
        DaySlots(date, parseSlots(m["slots"]))
    },
)

/** Termin iz odgovora callable funkcije (serialize() na serveru). */
fun parseAppointment(m: Map<*, *>): Appointment {
    val start = Instant.ofEpochMilli((m["start"] as Number).toLong())
    return Appointment(
        id = m["id"] as String,
        salonId = m["salonId"] as? String ?: "",
        salonName = m["salonName"] as? String ?: "",
        salonPhone = "",
        salonAddress = "",
        stylistId = m["stylistId"] as? String ?: "",
        stylistName = m["stylistName"] as? String ?: "",
        customerUid = null,
        customerName = "",
        customerPhone = null,
        services = (m["services"] as? List<*>).orEmpty().mapNotNull { s ->
            val sm = s as? Map<*, *> ?: return@mapNotNull null
            BookedService(sm["id"] as? String ?: "", sm["name"] as? String ?: "", (sm["price"] as? Number)?.toInt() ?: 0, (sm["durationMin"] as? Number)?.toInt() ?: 0)
        },
        start = start,
        end = Instant.ofEpochMilli((m["end"] as? Number)?.toLong() ?: start.toEpochMilli()),
        durationMin = (m["durationMin"] as? Number)?.toInt() ?: 0,
        totalPrice = (m["totalPrice"] as? Number)?.toInt() ?: 0,
        status = AppointmentStatus.from(m["status"] as? String),
        holdExpiresAt = null,
        note = m["note"] as? String ?: "",
        source = AppointmentSource.App,
        anyStylist = false,
        reviewed = false,
        dateKey = m["dateKey"] as? String ?: "",
    )
}
