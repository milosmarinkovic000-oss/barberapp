package rs.termin.app.core.data.staff

import android.net.Uri
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import rs.termin.app.core.data.booking.parseAppointment
import rs.termin.app.core.data.firebase.FunctionsClient
import rs.termin.app.core.data.firebase.formatHexColor
import rs.termin.app.core.data.firebase.snapshots
import rs.termin.app.core.data.firebase.toFirestore
import rs.termin.app.core.data.firebase.toTimeOff
import rs.termin.app.core.data.images.ImageUploader
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.DayPoint
import rs.termin.app.core.domain.model.RankedItem
import rs.termin.app.core.domain.model.SalonStats
import rs.termin.app.core.domain.model.StatsPeriod
import rs.termin.app.core.domain.model.TimeOff
import rs.termin.app.core.domain.model.TimeOffKind
import rs.termin.app.core.domain.repository.BlockResult
import rs.termin.app.core.domain.repository.ManualBooking
import rs.termin.app.core.domain.repository.SalonEdit
import rs.termin.app.core.domain.repository.ServiceEdit
import rs.termin.app.core.domain.repository.StaffRepository
import rs.termin.app.core.domain.repository.StylistEdit
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseStaffRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FunctionsClient,
    private val uploader: ImageUploader,
) : StaffRepository {

    private fun salon(salonId: String) = firestore.collection("salons").document(salonId)
    private fun stylist(salonId: String, stylistId: String) = salon(salonId).collection("stylists").document(stylistId)

    override suspend fun updateSalon(salonId: String, edit: SalonEdit) {
        salon(salonId).update(
            mapOf(
                "name" to edit.name.trim(),
                "description" to edit.description.trim(),
                "address" to edit.address.trim(),
                "city" to edit.city.trim(),
                "geo" to edit.location?.let { GeoPoint(it.latitude, it.longitude) },
                "phone" to edit.phone.trim(),
                "workingHours" to edit.workingHours.toFirestore(),
                "rules" to edit.rules.toFirestore(),
                "published" to edit.published,
                "priceLevel" to edit.priceLevel,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun uploadSalonPhoto(salonId: String, uri: Uri): String =
        uploader.upload(uri, "salons/$salonId/photos/${UUID.randomUUID()}.webp")

    override suspend fun setSalonPhotos(salonId: String, photos: List<String>) {
        salon(salonId).update(mapOf("photos" to photos, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun setSalonBranding(salonId: String, coverPhoto: String?, accentColor: Long) {
        salon(salonId).update(
            mapOf("coverPhoto" to coverPhoto, "accentColor" to formatHexColor(accentColor), "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    override suspend fun upsertService(salonId: String, serviceId: String?, edit: ServiceEdit) {
        val collection = salon(salonId).collection("services")
        val ref = if (serviceId == null) collection.document() else collection.document(serviceId)
        ref.set(
            mapOf(
                "category" to edit.category.key,
                "name" to edit.name.trim(),
                "description" to edit.description.trim(),
                "durationMin" to edit.durationMin,
                "price" to edit.price,
                "stylistIds" to edit.stylistIds,
                "active" to edit.active,
                "order" to edit.order,
            ),
        ).await()
    }

    override suspend fun deleteService(salonId: String, serviceId: String) {
        salon(salonId).collection("services").document(serviceId).delete().await()
    }

    override suspend fun inviteStylist(salonId: String, phoneE164: String, name: String): Boolean {
        val result = functions.call("inviteStylist", mapOf("salonId" to salonId, "phone" to phoneE164, "name" to name))
        return result["linked"] == true
    }

    override suspend fun updateStylist(salonId: String, stylistId: String, edit: StylistEdit) {
        stylist(salonId, stylistId).update(
            mapOf(
                "name" to edit.name.trim(),
                "bio" to edit.bio.trim(),
                "specialties" to edit.specialties,
                "workingHours" to edit.workingHours?.toFirestore(),
                "breaks" to edit.breaks.toFirestore(),
                "active" to edit.active,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun updateStylistProfile(salonId: String, stylistId: String, bio: String, specialties: List<String>) {
        stylist(salonId, stylistId).update(
            mapOf("bio" to bio.trim(), "specialties" to specialties, "updatedAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    override suspend fun uploadStylistPhoto(salonId: String, stylistId: String, uri: Uri, portfolio: Boolean): String {
        val name = if (portfolio) "portfolio-${UUID.randomUUID()}" else "portrait-${UUID.randomUUID()}"
        val path = uploader.upload(uri, "salons/$salonId/stylists/$stylistId/$name.webp")
        if (!portfolio) {
            stylist(salonId, stylistId).update(mapOf("photoPath" to path, "updatedAt" to FieldValue.serverTimestamp())).await()
        }
        return path
    }

    override suspend fun setStylistPortfolio(salonId: String, stylistId: String, portfolio: List<String>) {
        stylist(salonId, stylistId).update(mapOf("portfolio" to portfolio, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    override suspend fun setStylistAccess(salonId: String, stylistId: String, enabled: Boolean) {
        functions.call(
            "setUserRole",
            mapOf("salonId" to salonId, "stylistId" to stylistId, "role" to if (enabled) "stylist" else "customer"),
        )
    }

    override fun observeTimeOff(salonId: String, stylistId: String): Flow<List<TimeOff>> =
        stylist(salonId, stylistId).collection("timeOff")
            .orderBy("start", Query.Direction.DESCENDING)
            .limit(TIME_OFF_LIMIT)
            .snapshots()
            .map { snap -> snap.documents.map { it.toTimeOff(stylistId) } }

    override suspend fun blockTime(salonId: String, stylistId: String, start: Instant, end: Instant, kind: TimeOffKind, reason: String): BlockResult {
        val result = functions.call(
            "blockTime",
            mapOf(
                "salonId" to salonId,
                "stylistId" to stylistId,
                "start" to start.toEpochMilli(),
                "end" to end.toEpochMilli(),
                "kind" to kind.key,
                "reason" to reason,
            ),
        )
        return BlockResult(result["timeOffId"] as String, (result["conflicts"] as? List<*>)?.size ?: 0)
    }

    override suspend fun removeTimeOff(salonId: String, stylistId: String, timeOffId: String) {
        functions.call("removeTimeOff", mapOf("salonId" to salonId, "stylistId" to stylistId, "timeOffId" to timeOffId))
    }

    override suspend fun createManualBooking(booking: ManualBooking): Appointment {
        val result = functions.call(
            "createManualBooking",
            mapOf(
                "salonId" to booking.salonId,
                "stylistId" to booking.stylistId,
                "serviceIds" to booking.serviceIds,
                "start" to booking.start.toEpochMilli(),
                "customerName" to booking.customerName,
                "customerPhone" to booking.customerPhone,
                "note" to booking.note,
                "walkIn" to booking.walkIn,
            ),
        )
        return parseAppointment(result["appointment"] as Map<*, *>)
    }

    override suspend fun updateStatus(appointmentId: String, status: AppointmentStatus, reason: String) {
        functions.call("updateAppointmentStatus", mapOf("appointmentId" to appointmentId, "status" to status.key, "reason" to reason))
    }

    override suspend fun replyToReview(salonId: String, reviewId: String, text: String) {
        functions.call("replyToReview", mapOf("salonId" to salonId, "reviewId" to reviewId, "text" to text))
    }

    override suspend fun stats(salonId: String, period: StatsPeriod, date: LocalDate): SalonStats {
        val r = functions.callWithRetry("getSalonStats", mapOf("salonId" to salonId, "period" to period.key, "date" to date.toString()))
        fun ranked(value: Any?) = (value as? List<*>).orEmpty().mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            RankedItem(m["id"] as? String ?: "", m["name"] as? String ?: "", (m["count"] as? Number)?.toInt() ?: 0)
        }
        return SalonStats(
            total = (r["total"] as? Number)?.toInt() ?: 0,
            completed = (r["completed"] as? Number)?.toInt() ?: 0,
            noShow = (r["noShow"] as? Number)?.toInt() ?: 0,
            canceled = (r["canceled"] as? Number)?.toInt() ?: 0,
            revenueEstimate = (r["revenueEstimate"] as? Number)?.toInt() ?: 0,
            noShowRate = (r["noShowRate"] as? Number)?.toDouble() ?: 0.0,
            topServices = ranked(r["topServices"]),
            topStylists = ranked(r["topStylists"]),
            series = (r["series"] as? List<*>).orEmpty().mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                DayPoint(LocalDate.parse(m["date"] as String), (m["count"] as? Number)?.toInt() ?: 0, (m["revenue"] as? Number)?.toInt() ?: 0)
            },
        )
    }

    private companion object {
        const val TIME_OFF_LIMIT = 50L
    }
}
