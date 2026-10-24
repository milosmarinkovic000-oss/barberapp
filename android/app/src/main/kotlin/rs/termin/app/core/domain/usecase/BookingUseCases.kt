package rs.termin.app.core.domain.usecase

import rs.termin.app.core.domain.canCustomerChange
import rs.termin.app.core.domain.model.Appointment
import rs.termin.app.core.domain.model.Availability
import rs.termin.app.core.domain.model.BookingError
import rs.termin.app.core.domain.model.Hold
import rs.termin.app.core.domain.model.SalonRules
import rs.termin.app.core.domain.repository.BookingRepository
import rs.termin.app.core.domain.repository.PreferencesRepository
import rs.termin.app.core.domain.repository.ReminderScheduler
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Broj dana na traci izbora datuma. */
const val DAYS_STRIP = 14

/** Dostupnost za 14 dana (od danas ili od izabranog dana). */
class GetAvailabilityUseCase @Inject constructor(private val booking: BookingRepository) {
    suspend operator fun invoke(
        salonId: String,
        serviceIds: List<String>,
        stylistId: String?,
        from: LocalDate,
        excludeAppointmentId: String? = null,
    ): Availability = booking.availability(salonId, serviceIds, stylistId, from, DAYS_STRIP, excludeAppointmentId)
}

/**
 * Zaključava izabrani slot na 5 minuta. Ako korisnik bira novi slot, prethodni hold se
 * oslobađa u istoj serverskoj transakciji.
 */
class HoldSlotUseCase @Inject constructor(private val booking: BookingRepository) {
    suspend operator fun invoke(salonId: String, serviceIds: List<String>, stylistId: String?, start: Instant, current: Hold?): Hold =
        booking.holdSlot(salonId, serviceIds, stylistId, start, current?.appointmentId)
}

/** Potvrda termina + lokalni podsetnici (24 h i 2 h pre) + oznaka prvog zakazivanja. */
class ConfirmBookingUseCase @Inject constructor(
    private val booking: BookingRepository,
    private val reminders: ReminderScheduler,
    private val preferences: PreferencesRepository,
) {
    suspend operator fun invoke(hold: Hold, note: String): Appointment {
        val appointment = booking.confirmBooking(hold.appointmentId, note.trim())
        reminders.schedule(appointment)
        preferences.setFirstBookingDone()
        return appointment
    }
}

/** Promena termina: server atomski proverava novi slot i rok iz pravila salona. */
class RescheduleUseCase @Inject constructor(
    private val booking: BookingRepository,
    private val reminders: ReminderScheduler,
) {
    suspend operator fun invoke(appointmentId: String, start: Instant, stylistId: String?): Appointment {
        val appointment = booking.reschedule(appointmentId, start, stylistId)
        reminders.cancel(appointmentId)
        reminders.schedule(appointment)
        return appointment
    }
}

sealed interface CancelResult {
    data object Canceled : CancelResult

    /** Rok je prošao: aplikacija nudi „Pozovi salon“. */
    data class CallSalon(val phone: String) : CancelResult
}

/** Otkazivanje po pravilima salona; posle roka se ne šalje zahtev, već se nudi poziv. */
class CancelAppointmentUseCase @Inject constructor(
    private val booking: BookingRepository,
    private val reminders: ReminderScheduler,
    private val clock: Clock,
) {
    suspend operator fun invoke(appointment: Appointment, rules: SalonRules): CancelResult {
        if (!canCustomerChange(appointment, rules, clock.instant())) return CancelResult.CallSalon(appointment.salonPhone)
        return try {
            booking.cancel(appointment.id)
            reminders.cancel(appointment.id)
            CancelResult.Canceled
        } catch (e: BookingError.PolicyViolation) {
            CancelResult.CallSalon(e.salonPhone ?: appointment.salonPhone)
        }
    }
}
