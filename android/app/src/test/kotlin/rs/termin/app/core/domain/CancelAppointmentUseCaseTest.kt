package rs.termin.app.core.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import rs.termin.app.core.domain.model.BookingError
import rs.termin.app.core.domain.model.PolicyReason
import rs.termin.app.core.domain.model.SalonRules
import rs.termin.app.core.domain.repository.BookingRepository
import rs.termin.app.core.domain.repository.ReminderScheduler
import rs.termin.app.core.domain.usecase.CancelAppointmentUseCase
import rs.termin.app.core.domain.usecase.CancelResult
import rs.termin.app.testing.TestData
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

class CancelAppointmentUseCaseTest {
    private val day = LocalDate.of(2026, 11, 2)
    private val start = TestData.at(day, 18)
    private val rules = SalonRules.Default.copy(cancelHours = 12)
    private val booking = mockk<BookingRepository>()
    private val reminders = mockk<ReminderScheduler> { every { cancel(any()) } just runs }

    private fun useCase(hoursBefore: Long) =
        CancelAppointmentUseCase(booking, reminders, Clock.fixed(start.minus(Duration.ofHours(hoursBefore)), ZoneOffset.UTC))

    @Test
    fun `u roku - otkazuje na serveru i brise podsetnike`() = runTest {
        coEvery { booking.cancel("a1", any()) } just runs
        val result = useCase(hoursBefore = 24)(TestData.appointment("a1", start), rules)
        assertEquals(CancelResult.Canceled, result)
        coVerify(exactly = 1) { booking.cancel("a1", "") }
        verify { reminders.cancel("a1") }
    }

    @Test
    fun `posle roka - bez zahteva serveru, nudi poziv salona`() = runTest {
        val result = useCase(hoursBefore = 2)(TestData.appointment("a1", start), rules)
        assertEquals(CancelResult.CallSalon("+381601234567"), result)
        coVerify(exactly = 0) { booking.cancel(any(), any()) }
        verify(exactly = 0) { reminders.cancel(any()) }
    }

    @Test
    fun `server odbije zbog roka - nudi poziv sa telefonom iz odgovora`() = runTest {
        coEvery { booking.cancel(any(), any()) } throws BookingError.PolicyViolation(PolicyReason.CancelDeadline, "+381111234567", null)
        val result = useCase(hoursBefore = 13)(TestData.appointment("a1", start), rules)
        assertEquals(CancelResult.CallSalon("+381111234567"), result)
    }
}
