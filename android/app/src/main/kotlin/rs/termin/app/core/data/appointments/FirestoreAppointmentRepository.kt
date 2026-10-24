package rs.termin.app.core.data.appointments

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import rs.termin.app.core.data.firebase.snapshots
import rs.termin.app.core.data.firebase.toAppointment
import rs.termin.app.core.data.firebase.toWaitlistEntry
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.WaitlistEntry
import rs.termin.app.core.domain.repository.AppointmentRepository
import java.time.Instant
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreAppointmentRepository @Inject constructor(private val firestore: FirebaseFirestore) : AppointmentRepository {

    private val appointments get() = firestore.collection("appointments")

    override fun observeMine(uid: String): Flow<List<Appointment>> =
        appointments.whereEqualTo("customerUid", uid)
            .orderBy("start", Query.Direction.DESCENDING)
            .limit(MY_LIMIT)
            .snapshots()
            .map { snap -> snap.documents.map { it.toAppointment() } }

    override fun observeAppointment(appointmentId: String): Flow<Appointment?> =
        appointments.document(appointmentId).snapshots().map { if (it.exists()) it.toAppointment() else null }

    override fun observeSalonAppointments(salonId: String, from: Instant, to: Instant, stylistId: String?): Flow<List<Appointment>> {
        var query = appointments.whereEqualTo("salonId", salonId)
        if (stylistId != null) query = query.whereEqualTo("stylistId", stylistId)
        return query
            .whereGreaterThanOrEqualTo("start", Timestamp(Date.from(from)))
            .whereLessThan("start", Timestamp(Date.from(to)))
            .orderBy("start")
            .snapshots()
            .map { snap -> snap.documents.map { it.toAppointment() } }
    }

    override suspend fun customerHistory(salonId: String, customerUid: String?, customerPhone: String?): List<Appointment> {
        val query = when {
            customerUid != null -> appointments.whereEqualTo("salonId", salonId).whereEqualTo("customerUid", customerUid)
            customerPhone != null -> appointments.whereEqualTo("salonId", salonId).whereEqualTo("customerPhone", customerPhone)
            else -> return emptyList()
        }
        return query.orderBy("start", Query.Direction.DESCENDING).limit(HISTORY_LIMIT).get().await().documents.map { it.toAppointment() }
    }

    override fun observeMyWaitlist(uid: String): Flow<List<WaitlistEntry>> =
        firestore.collection("waitlist").whereEqualTo("uid", uid).snapshots()
            .map { snap -> snap.documents.map { it.toWaitlistEntry() }.sortedBy { it.date } }

    private companion object {
        const val MY_LIMIT = 50L
        const val HISTORY_LIMIT = 20L
    }
}
