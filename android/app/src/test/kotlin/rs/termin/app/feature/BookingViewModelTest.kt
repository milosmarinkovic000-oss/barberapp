package rs.termin.app.feature

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import rs.termin.app.core.domain.model.BookingError
import rs.termin.app.core.domain.usecase.ConfirmBookingUseCase
import rs.termin.app.core.domain.usecase.GetAvailabilityUseCase
import rs.termin.app.core.domain.usecase.HoldSlotUseCase
import rs.termin.app.core.domain.usecase.RescheduleUseCase
import rs.termin.app.feature.booking.BookingStep
import rs.termin.app.feature.booking.BookingViewModel
import rs.termin.app.testing.FakeAppointmentRepository
import rs.termin.app.testing.FakeBookingRepository
import rs.termin.app.testing.FakeConnectivity
import rs.termin.app.testing.FakePreferences
import rs.termin.app.testing.FakeReminders
import rs.termin.app.testing.FakeSalonRepository
import rs.termin.app.testing.TestData
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BookingViewModelTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val clock = Clock.fixed(TestData.at(today, 8), ZoneOffset.UTC)
    private val dispatcher = UnconfinedTestDispatcher()
    private val salons = FakeSalonRepository()
    private val booking = FakeBookingRepository(clock, salons)
    private val appointments = FakeAppointmentRepository(booking)
    private val reminders = FakeReminders()
    private val preferences = FakePreferences()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(services: String? = "cut", stylist: String? = null, rescheduleId: String? = null): BookingViewModel {
        val handle = SavedStateHandle(
            mapOf("salonId" to "s1", "services" to services, "stylist" to stylist, "date" to null, "rescheduleId" to rescheduleId),
        )
        val vm = BookingViewModel(
            savedStateHandle = handle,
            salons = salons,
            appointments = appointments,
            booking = booking,
            getAvailability = GetAvailabilityUseCase(booking),
            holdSlot = HoldSlotUseCase(booking),
            confirmBooking = ConfirmBookingUseCase(booking, reminders, preferences),
            reschedule = RescheduleUseCase(booking, reminders),
            connectivity = FakeConnectivity(),
            clock = clock,
            appScope = CoroutineScope(dispatcher),
        )
        backgroundScope.launch(dispatcher) { vm.uiState.collect { } }
        return vm
    }

    @Test
    fun `ceo tok - usluga, bilo koji frizer, slot, potvrda`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(BookingStep.Stylist, vm.uiState.value.step)

        vm.selectStylist(null)
        vm.next()
        val time = vm.uiState.value
        assertEquals(BookingStep.Time, time.step)
        assertEquals(today, time.selectedDate)
        val slot = time.selectedDay!!.slots.first()

        vm.selectSlot(slot)
        val held = vm.uiState.value
        assertNotNull(held.hold)
        assertTrue(held.canContinue)

        vm.next()
        assertEquals(BookingStep.Review, vm.uiState.value.step)
        vm.onNote("  Kraće sa strane  ")
        vm.confirm()

        val done = vm.uiState.value
        assertEquals(held.hold!!.appointmentId, done.completedAppointmentId)
        assertEquals("Kraće sa strane", booking.confirmed.single().note)
        assertEquals(listOf(done.completedAppointmentId), reminders.scheduled)
        assertTrue(preferences.state.value.firstBookingDone)
    }

    @Test
    fun `zauzet slot vraca osvezene slotove i ne pravi hold`() = runTest(dispatcher) {
        val vm = viewModel(stylist = "a")
        assertEquals(BookingStep.Time, vm.uiState.value.step)
        val slot = vm.uiState.value.selectedDay!!.slots.first()
        booking.takenByOthers += slot.start

        vm.selectSlot(slot)

        val state = vm.uiState.value
        assertTrue(state.error is BookingError.SlotTaken)
        assertNull(state.hold)
        assertFalse(state.selectedDay!!.slots.any { it.start == slot.start })
    }

    @Test
    fun `promena usluga oslobadja zauzet slot`() = runTest(dispatcher) {
        val vm = viewModel(stylist = "a")
        vm.selectSlot(vm.uiState.value.selectedDay!!.slots.first())
        val holdId = vm.uiState.value.hold!!.appointmentId

        vm.toggleService("cut")

        assertNull(vm.uiState.value.hold)
        assertEquals(listOf(holdId), booking.released)
    }

    @Test
    fun `promena termina ne zakljucava slot i menja postojeci termin`() = runTest(dispatcher) {
        val existing = TestData.appointment(id = "old", start = TestData.at(today.plusDays(3), 15))
        appointments.mine.value = listOf(existing)
        booking.confirmed += existing
        val vm = viewModel(services = null, rescheduleId = "old")

        val state = vm.uiState.value
        assertEquals(listOf(BookingStep.Time, BookingStep.Review), state.steps)
        val slot = state.selectedDay!!.slots.first()
        vm.selectSlot(slot)
        assertNull(vm.uiState.value.hold)
        assertTrue(booking.holds.isEmpty())

        vm.next()
        vm.confirm()

        assertEquals("old", vm.uiState.value.completedAppointmentId)
        assertEquals(slot.start, booking.confirmed.single { it.id == "old" }.start)
        assertEquals(listOf("old"), reminders.canceled)
        assertEquals(listOf("old"), reminders.scheduled)
    }

    @Test
    fun `lista cekanja za izabrani dan`() = runTest(dispatcher) {
        val vm = viewModel(stylist = "a")
        vm.selectDate(today.plusDays(2))
        vm.joinWaitlist()
        assertTrue(vm.uiState.value.waitlistJoined)
        assertEquals(today.plusDays(2) to listOf("cut"), booking.waitlist.single())
    }
}
