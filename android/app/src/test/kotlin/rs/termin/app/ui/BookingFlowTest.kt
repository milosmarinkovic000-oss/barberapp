package rs.termin.app.ui

import android.content.Context
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rs.termin.app.R
import rs.termin.app.core.domain.usecase.ConfirmBookingUseCase
import rs.termin.app.core.domain.usecase.GetAvailabilityUseCase
import rs.termin.app.core.domain.usecase.HoldSlotUseCase
import rs.termin.app.core.domain.usecase.RescheduleUseCase
import rs.termin.app.feature.booking.BookingScreen
import rs.termin.app.feature.booking.BookingViewModel
import rs.termin.app.testing.FakeAppointmentRepository
import rs.termin.app.testing.FakeBookingRepository
import rs.termin.app.testing.FakeConnectivity
import rs.termin.app.testing.FakePreferences
import rs.termin.app.testing.FakeReminders
import rs.termin.app.testing.FakeSalonRepository
import rs.termin.app.testing.TestApp
import rs.termin.app.testing.TestData
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Ceo tok zakazivanja kroz interfejs: usluga → frizer → termin (hold) → napomena → potvrda. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "b+sr+Latn-w411dp-h891dp")
class BookingFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 11, 2)
    private val clock = Clock.fixed(TestData.at(today, 8), ZoneOffset.UTC)
    private val salons = FakeSalonRepository()
    private val booking = FakeBookingRepository(clock, salons)
    private val reminders = FakeReminders()

    private fun viewModel() = BookingViewModel(
        savedStateHandle = SavedStateHandle(mapOf("salonId" to "s1", "services" to null, "stylist" to null, "date" to null, "rescheduleId" to null)),
        salons = salons,
        appointments = FakeAppointmentRepository(booking),
        booking = booking,
        getAvailability = GetAvailabilityUseCase(booking),
        holdSlot = HoldSlotUseCase(booking),
        confirmBooking = ConfirmBookingUseCase(booking, reminders, FakePreferences()),
        reschedule = RescheduleUseCase(booking, reminders),
        connectivity = FakeConnectivity(),
        clock = clock,
        appScope = CoroutineScope(Dispatchers.Unconfined),
    )

    @Test
    fun zakazivanjeOdPocetkaDoPotvrde() {
        var completed: String? = null
        val vm = viewModel()
        compose.setContent { TestApp { BookingScreen(onClose = {}, onCompleted = { completed = it }, viewModel = vm) } }
        val next = context.getString(R.string.booking_next)

        compose.onNodeWithText(context.getString(R.string.booking_services_title)).assertExists()
        compose.onNodeWithText("Usluga cut").performClick()
        compose.onNodeWithText(next).performClick()

        compose.onNodeWithText(context.getString(R.string.stylist_any)).performClick()
        compose.onNodeWithText(next).performClick()

        compose.onNodeWithText(context.getString(R.string.booking_time_title)).assertExists()
        compose.onAllNodes(hasClickAction() and hasContentDescription("10:30", substring = true))[0].performClick()
        compose.waitForIdle()
        assertEquals(1, booking.holds.size)
        compose.onNodeWithText(next).performClick()

        compose.onNodeWithText(context.getString(R.string.booking_review_title)).assertExists()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("Kratko sa strane")
        compose.onAllNodesWithText(context.getString(R.string.booking_confirm))[0].performClick()
        compose.waitForIdle()

        val appointment = booking.confirmed.single()
        assertEquals(appointment.id, completed)
        assertEquals("Kratko sa strane", appointment.note)
        assertEquals(TestData.at(today, 10, 30), appointment.start)
        assertEquals(listOf(appointment.id), reminders.scheduled)
    }

    @Test
    fun zauzetSlotPrikazujePorukuIOsvezeneTermine() {
        val vm = viewModel()
        booking.takenByOthers += TestData.at(today, 10, 30)
        compose.setContent { TestApp { BookingScreen(onClose = {}, onCompleted = {}, viewModel = vm) } }
        compose.onNodeWithText("Usluga cut").performClick()
        compose.onNodeWithText(context.getString(R.string.booking_next)).performClick()
        compose.onNodeWithText(context.getString(R.string.stylist_any)).performClick()
        compose.onNodeWithText(context.getString(R.string.booking_next)).performClick()
        // Drugi klijent je upravo uzeo 11:00 — server odbija i šalje osvežene slotove.
        booking.takenByOthers += TestData.at(today, 11)
        compose.onAllNodes(hasClickAction() and hasContentDescription("11:00", substring = true))[0].performClick()
        compose.waitForIdle()

        compose.onNode(hasText(context.getString(R.string.error_slot_taken))).assertExists()
        assertEquals(0, booking.holds.size)
        assertEquals(0, compose.onAllNodes(hasContentDescription("11:00", substring = true)).fetchSemanticsNodes().size)
    }
}
