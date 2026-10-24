package rs.termin.app.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.InboxMessage
import rs.termin.app.core.domain.model.StatsPeriod
import rs.termin.app.core.domain.model.TimeRange
import rs.termin.app.core.domain.model.WeeklyHours
import rs.termin.app.feature.appointments.splitAppointments
import rs.termin.app.feature.calendar.hourRange
import rs.termin.app.feature.calendar.visibleInCalendar
import rs.termin.app.feature.inbox.InboxTarget
import rs.termin.app.feature.inbox.target
import rs.termin.app.feature.owner.isValid
import rs.termin.app.feature.owner.niceCeiling
import rs.termin.app.feature.owner.periodStart
import rs.termin.app.feature.staff.StaffFilter
import rs.termin.app.feature.staff.staffRange
import rs.termin.app.testing.TestData
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class StaffLogicTest {
    private val day = LocalDate.of(2026, 10, 26) // ponedeljak, dan posle povratka na zimsko vreme

    @Test
    fun `kalendar ne prikazuje otkazane i istekla zadrzavanja`() {
        val now = TestData.at(day, 9)
        val base = TestData.appointment(start = TestData.at(day, 10))
        assertTrue(visibleInCalendar(base, now))
        assertFalse(visibleInCalendar(base.copy(status = AppointmentStatus.CanceledBySalon), now))
        assertFalse(visibleInCalendar(base.copy(status = AppointmentStatus.Held, holdExpiresAt = now.minusSeconds(1)), now))
        assertTrue(visibleInCalendar(base.copy(status = AppointmentStatus.Held, holdExpiresAt = now.plusSeconds(60)), now))
    }

    @Test
    fun `mreza sati obuhvata radno vreme i termine van njega`() {
        val salon = TestData.salon()
        assertEquals(9 until 20, hourRange(salon, emptyList(), day, emptyList()))
        val late = TestData.appointment(start = TestData.at(day, 20, 30), durationMin = 60)
        assertEquals(9 until 22, hourRange(salon, emptyList(), day, listOf(late)))
        // Nedelja: salon ne radi, podrazumevani opseg.
        assertEquals(8 until 20, hourRange(salon, emptyList(), day.minusDays(1), emptyList()))
    }

    @Test
    fun `opsezi filtera osoblja po beogradskom vremenu`() {
        val (from, to) = staffRange(StaffFilter.Today, day)
        assertEquals(Duration.ofHours(24), Duration.between(from, to))
        val (pFrom, pTo) = staffRange(StaffFilter.Past, day)
        assertEquals(from, pTo)
        assertTrue(pFrom.isBefore(from))
        val (uFrom, _) = staffRange(StaffFilter.Upcoming, day)
        assertEquals(to, uFrom)
    }

    @Test
    fun `pocetak perioda statistike`() {
        val thursday = LocalDate.of(2026, 10, 29)
        assertEquals(thursday, periodStart(StatsPeriod.Day, thursday))
        assertEquals(LocalDate.of(2026, 10, 26), periodStart(StatsPeriod.Week, thursday))
        assertEquals(LocalDate.of(2026, 10, 1), periodStart(StatsPeriod.Month, thursday))
    }

    @Test
    fun `cist vrh ose`() {
        assertEquals(1, niceCeiling(0))
        assertEquals(1, niceCeiling(1))
        assertEquals(5, niceCeiling(3))
        assertEquals(10, niceCeiling(7))
        assertEquals(20, niceCeiling(13))
        assertEquals(50, niceCeiling(42))
        assertEquals(100, niceCeiling(100))
    }

    @Test
    fun `radno vreme bez preklapanja i sa krajem posle pocetka`() {
        fun range(a: Int, b: Int) = TimeRange(LocalTime.of(a, 0), LocalTime.of(b, 0))
        assertTrue(WeeklyHours(mapOf(DayOfWeek.MONDAY to listOf(range(9, 13), range(14, 18)))).isValid())
        assertFalse(WeeklyHours(mapOf(DayOfWeek.MONDAY to listOf(range(9, 13), range(12, 18)))).isValid())
        assertFalse(WeeklyHours(mapOf(DayOfWeek.MONDAY to listOf(range(18, 9)))).isValid())
    }

    @Test
    fun `dodir na obavestenje vodi na pravo mesto`() {
        fun message(type: String, appointment: String? = null, salon: String? = null) =
            InboxMessage("m", type, "t", "b", false, Instant.EPOCH, appointment, salon, "2026-10-27", listOf("cut"), "a")
        assertEquals(InboxTarget.Appointment("x", staff = false), message("booking_confirmed", appointment = "x", salon = "s").target())
        assertEquals(InboxTarget.Appointment("x", staff = true), message("staff_new_booking", appointment = "x", salon = "s").target())
        assertEquals(InboxTarget.Book("s", "2026-10-27", listOf("cut"), "a"), message("waitlist_slot", salon = "s").target())
        assertEquals(InboxTarget.Salon("s"), message("review_reply", salon = "s").target())
        assertEquals(InboxTarget.Nowhere, message("stylist_invited", salon = "s").target())
    }

    @Test
    fun `predstojeci rastuce, prosli opadajuce`() {
        val now = TestData.at(day, 12)
        val past1 = TestData.appointment("p1", TestData.at(day.minusDays(2), 10))
        val past2 = TestData.appointment("p2", TestData.at(day.minusDays(1), 10))
        val next1 = TestData.appointment("n1", TestData.at(day.plusDays(1), 10))
        val next2 = TestData.appointment("n2", TestData.at(day, 15))
        val canceled = TestData.appointment("c", TestData.at(day.plusDays(2), 10), status = AppointmentStatus.CanceledByCustomer)
        val (upcoming, past) = splitAppointments(listOf(past1, next1, canceled, past2, next2), now)
        assertEquals(listOf("n2", "n1"), upcoming.map { it.id })
        assertEquals(listOf("c", "p2", "p1"), past.map { it.id })
    }
}
