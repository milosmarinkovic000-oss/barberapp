package rs.termin.app.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.DaySlots
import rs.termin.app.core.domain.model.GeoLocation
import rs.termin.app.core.domain.model.Role
import rs.termin.app.core.domain.model.SalonRules
import rs.termin.app.core.domain.model.Slot
import rs.termin.app.core.domain.repository.AuthState
import rs.termin.app.testing.TestData
import java.time.Duration
import java.time.LocalDate

class BookingRulesTest {
    private val day = LocalDate.of(2026, 3, 30) // ponedeljak, posle prelaska na letnje vreme

    @Test
    fun `slotovi se grupisu po delu dana po beogradskom vremenu`() {
        val slots = listOf(9, 11, 12, 16, 17, 19).map { Slot(TestData.at(day, it), listOf("a")) }
        val groups = groupByDayPart(slots)
        assertEquals(2, groups.getValue(DayPart.Morning).size)
        assertEquals(2, groups.getValue(DayPart.Afternoon).size)
        assertEquals(2, groups.getValue(DayPart.Evening).size)
    }

    @Test
    fun `prvi slobodan slot preskace prazne dane`() {
        val days = listOf(
            DaySlots(day, emptyList()),
            DaySlots(day.plusDays(1), listOf(Slot(TestData.at(day.plusDays(1), 10), listOf("a")))),
        )
        assertEquals(day.plusDays(1), firstFreeSlot(days)?.first)
        assertNull(firstFreeSlot(listOf(DaySlots(day, emptyList()))))
    }

    @Test
    fun `zbir trajanja i cene`() {
        val totals = totalsOf(listOf(TestData.service("x", duration = 30, price = 1500), TestData.service("y", duration = 45, price = 2500)))
        assertEquals(75, totals.durationMin)
        assertEquals(4000, totals.price)
    }

    @Test
    fun `bilo koji frizer mora da radi sve izabrane usluge`() {
        val services = listOf(TestData.service("x", listOf("a", "b")), TestData.service("y", listOf("b", "c")))
        assertEquals(listOf("b"), capableStylistIds(services, listOf("a", "b", "c")))
    }

    @Test
    fun `rok za otkazivanje`() {
        val rules = SalonRules.Default.copy(cancelHours = 12)
        val start = TestData.at(day, 18)
        val appointment = TestData.appointment(start = start)
        assertEquals(start.minus(Duration.ofHours(12)), cancellationDeadline(start, rules))
        assertTrue(canCustomerChange(appointment, rules, start.minus(Duration.ofHours(13))))
        assertTrue(canCustomerChange(appointment, rules, start.minus(Duration.ofHours(12))))
        assertFalse(canCustomerChange(appointment, rules, start.minus(Duration.ofHours(11))))
        assertFalse(canCustomerChange(appointment.copy(status = AppointmentStatus.CanceledBySalon), rules, start.minus(Duration.ofDays(2))))
    }

    @Test
    fun `ocena samo za zavrsen, neocenjen termin sa nalogom`() {
        val start = TestData.at(day, 10)
        assertTrue(canReview(TestData.appointment(start = start, status = AppointmentStatus.Completed)))
        assertFalse(canReview(TestData.appointment(start = start, status = AppointmentStatus.Completed, reviewed = true)))
        assertFalse(canReview(TestData.appointment(start = start, status = AppointmentStatus.Confirmed)))
        assertFalse(canReview(TestData.appointment(start = start, status = AppointmentStatus.Completed, customerUid = null)))
    }

    @Test
    fun `predstojeci dok ne prodje kraj termina`() {
        val start = TestData.at(day, 10)
        val a = TestData.appointment(start = start, durationMin = 60)
        assertTrue(isUpcoming(a, start.plus(Duration.ofMinutes(30))))
        assertFalse(isUpcoming(a, start.plus(Duration.ofMinutes(60))))
        assertFalse(isUpcoming(a.copy(status = AppointmentStatus.CanceledByCustomer), start.minus(Duration.ofHours(1))))
    }

    @Test
    fun `rastojanje Beograd - Novi Sad`() {
        val km = distanceKm(GeoLocation(44.8176, 20.4569), GeoLocation(45.2671, 19.8335))
        assertEquals(70.0, km, 3.0)
    }

    @Test
    fun `sesija osoblja iz uloga`() {
        val owner = AuthState.SignedIn("u", setOf(Role.Owner), "s1", null).staffSession()
        assertTrue(owner!!.isOwner)
        assertNull(owner.stylistFilter)
        assertTrue(owner.canManageStylist("bilo-ko"))

        val stylist = AuthState.SignedIn("u", setOf(Role.Stylist), "s1", "a").staffSession()!!
        assertEquals("a", stylist.stylistFilter)
        assertTrue(stylist.canManageStylist("a"))
        assertFalse(stylist.canManageStylist("b"))

        assertNull(AuthState.SignedIn("u", setOf(Role.Customer), null, null).staffSession())
        assertNull(AuthState.SignedOut.staffSession())
    }
}
