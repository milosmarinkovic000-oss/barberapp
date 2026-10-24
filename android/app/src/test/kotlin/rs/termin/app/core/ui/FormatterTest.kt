package rs.termin.app.core.ui

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.testing.TestData
import java.time.Duration
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class FormatterTest {
    private val f get() = Formatter(ApplicationProvider.getApplicationContext())
    private val tuesday = LocalDate.of(2026, 5, 12)

    @Test
    @Config(qualifiers = "b+sr+Latn")
    fun srpski() {
        assertEquals("1.200 RSD", f.price(1200))
        assertEquals("12.500 RSD", f.price(12500))
        assertEquals("950 RSD", f.price(950))
        assertEquals("utorak, 12. maj", f.dayLong(tuesday))
        assertEquals("1 h 30 min", f.duration(90))
        assertEquals("45 min", f.duration(45))
        assertEquals("2 h", f.duration(120))
        assertEquals("450 m", f.distance(0.447))
        assertEquals("2,3 km", f.distance(2.34))
        assertEquals("1 recenzija", f.reviews(1))
        assertEquals("3 recenzije", f.reviews(3))
        assertEquals("5 recenzija", f.reviews(5))
        assertEquals("21 recenzija", f.reviews(21))
        assertEquals("Potvrđen", f.status(AppointmentStatus.Confirmed))
    }

    @Test
    @Config(qualifiers = "b+sr+Latn")
    fun odbrojavanje() {
        val now = TestData.at(tuesday, 9)
        assertEquals("uskoro", f.countdown(now.plusSeconds(20), now))
        assertEquals("za 25 minuta", f.countdown(now.plus(Duration.ofMinutes(25)), now))
        assertEquals("za 2 sata", f.countdown(now.plus(Duration.ofHours(2)), now))
        assertEquals("danas u 18:30", f.countdown(TestData.at(tuesday, 18, 30), now))
        assertEquals("sutra u 10:00", f.countdown(TestData.at(tuesday.plusDays(1), 10), now))
        assertEquals("za 5 dana", f.countdown(TestData.at(tuesday.plusDays(5), 10), now))
    }

    @Test
    @Config(qualifiers = "en")
    fun english() {
        assertEquals("1,200 RSD", f.price(1200))
        assertEquals("Tuesday, 12 May", f.dayLong(tuesday))
        assertEquals("2.3 km", f.distance(2.34))
        assertEquals("1 review", f.reviews(1))
        assertEquals("4 reviews", f.reviews(4))
        val now = TestData.at(tuesday, 9)
        assertEquals("tomorrow at 10:00", f.countdown(TestData.at(tuesday.plusDays(1), 10), now))
    }
}
