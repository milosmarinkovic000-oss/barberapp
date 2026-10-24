package rs.termin.app.feature

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.Role
import rs.termin.app.core.domain.repository.AuthState
import rs.termin.app.core.ui.Loadable
import rs.termin.app.feature.appointments.AppointmentsViewModel
import rs.termin.app.testing.FakeAppointmentRepository
import rs.termin.app.testing.FakeAuthRepository
import rs.termin.app.testing.FakeBookingRepository
import rs.termin.app.testing.FakeConnectivity
import rs.termin.app.testing.FakeSalonRepository
import rs.termin.app.testing.TestData
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AppointmentsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.of(2026, 11, 2)
    private val clock = Clock.fixed(TestData.at(today, 12), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `lista se azurira uzivo kad salon otkaze termin`() = runTest(dispatcher) {
        val appointments = FakeAppointmentRepository()
        appointments.mine.value = listOf(
            TestData.appointment("next", TestData.at(today.plusDays(1), 10)),
            TestData.appointment("old", TestData.at(today.minusDays(5), 10), status = AppointmentStatus.Completed),
        )
        val vm = AppointmentsViewModel(
            auth = FakeAuthRepository(AuthState.SignedIn("u1", setOf(Role.Customer), null, null)),
            appointments = appointments,
            booking = FakeBookingRepository(clock, FakeSalonRepository()),
            connectivity = FakeConnectivity(),
            clock = clock,
        )

        vm.uiState.test {
            val ready = expectMostRecentItem().lists as Loadable.Ready
            assertEquals(listOf("next"), ready.value.upcoming.map { it.id })
            assertEquals(listOf("old"), ready.value.past.map { it.id })

            appointments.mine.value = appointments.mine.value.map {
                if (it.id == "next") it.copy(status = AppointmentStatus.CanceledBySalon) else it
            }
            val updated = awaitItem().lists as Loadable.Ready
            assertTrue(updated.value.upcoming.isEmpty())
            assertEquals(setOf("next", "old"), updated.value.past.map { it.id }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
