package rs.termin.app

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.Role
import rs.termin.app.core.domain.repository.AppPreferences
import rs.termin.app.core.domain.repository.AuthState
import rs.termin.app.testing.FakeAppointmentRepository
import rs.termin.app.testing.FakeAuthRepository
import rs.termin.app.testing.FakePreferences
import rs.termin.app.testing.FakeReminders
import rs.termin.app.testing.FakeUserRepository
import rs.termin.app.testing.TestData
import rs.termin.app.testing.TestProfiles
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.of(2026, 11, 2)
    private val clock = Clock.fixed(TestData.at(today, 8), ZoneOffset.UTC)
    private val auth = FakeAuthRepository()
    private val users = FakeUserRepository()
    private val appointments = FakeAppointmentRepository()
    private val preferences = FakePreferences()
    private val reminders = FakeReminders()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = AppViewModel(auth, users, appointments, preferences, reminders, clock)

    @Test
    fun `prvo pokretanje - uvod pa prijava`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(AppPhase.Onboarding, vm.uiState.value.phase)
        vm.finishOnboarding()
        assertEquals(AppPhase.SignIn, vm.uiState.value.phase)
    }

    @Test
    fun `nova prijava - uskladjivanje, profil, glavni deo`() = runTest(dispatcher) {
        preferences.state.value = AppPreferences(onboardingDone = true)
        appointments.mine.value = listOf(
            TestData.appointment("next", TestData.at(today.plusDays(1), 10)),
            TestData.appointment("old", TestData.at(today.minusDays(3), 10), status = AppointmentStatus.Completed),
        )
        val vm = viewModel()
        assertEquals(AppPhase.SignIn, vm.uiState.value.phase)

        auth.verifyCode("v", "123456")
        assertEquals(AppPhase.ProfileSetup, vm.uiState.value.phase)
        assertEquals(1, users.syncCalls)
        assertEquals(1, users.pushRegistrations)
        assertEquals(listOf("next"), reminders.scheduled)

        users.updateName("Marko Marković")
        assertEquals(AppPhase.Ready("u1", staff = null), vm.uiState.value.phase)
    }

    @Test
    fun `osoblje dobija prikaz za salon, a moze i kao klijent`() = runTest(dispatcher) {
        preferences.state.value = AppPreferences(onboardingDone = true)
        users.flow.value = TestProfiles.customer().copy(roles = setOf(Role.Owner), salonId = "s1")
        auth.state.value = AuthState.SignedIn("u1", setOf(Role.Owner), "s1", null)
        val vm = viewModel()

        val ready = vm.uiState.value.phase as AppPhase.Ready
        assertNotNull(ready.staff)
        assertTrue(ready.staff!!.isOwner)

        preferences.setClientView(true)
        assertNull((vm.uiState.value.phase as AppPhase.Ready).staff)
    }

    @Test
    fun `bez interneta pri prvoj prijavi - poruka i ponovni pokusaj`() = runTest(dispatcher) {
        preferences.state.value = AppPreferences(onboardingDone = true)
        users.syncFailure = IOException("offline")
        auth.state.value = AuthState.SignedIn("u1", setOf(Role.Customer), null, null)
        val vm = viewModel()
        assertTrue(vm.uiState.value.phase is AppPhase.SyncFailed)

        users.syncFailure = null
        vm.retrySync()
        assertEquals(AppPhase.ProfileSetup, vm.uiState.value.phase)
    }

    @Test
    fun `odjava brise lokalne podsetnike`() = runTest(dispatcher) {
        preferences.state.value = AppPreferences(onboardingDone = true)
        users.flow.value = TestProfiles.customer()
        appointments.mine.value = listOf(TestData.appointment("next", TestData.at(today.plusDays(1), 10)))
        auth.state.value = AuthState.SignedIn("u1", setOf(Role.Customer), null, null)
        val vm = viewModel()
        assertEquals(listOf("next"), reminders.scheduled)

        vm.signOut()
        assertEquals(AppPhase.SignIn, vm.uiState.value.phase)
        assertTrue(reminders.scheduled.isEmpty())
        assertEquals(listOf("next"), reminders.canceled)
    }

    @Test
    fun `deep link ceka glavni ekran`() = runTest(dispatcher) {
        val vm = viewModel()
        val link = Uri.parse("termin://salon/s1")
        vm.onDeepLink(link)
        assertEquals(link, vm.deepLink.value)
        vm.deepLinkHandled()
        assertNull(vm.deepLink.value)
    }
}
