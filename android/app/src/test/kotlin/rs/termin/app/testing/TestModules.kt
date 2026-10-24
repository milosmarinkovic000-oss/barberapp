package rs.termin.app.testing

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import rs.termin.app.core.common.ApplicationScope
import rs.termin.app.core.common.CommonModule
import rs.termin.app.core.common.DefaultDispatcher
import rs.termin.app.core.common.IoDispatcher
import rs.termin.app.core.data.DataModule
import rs.termin.app.core.domain.repository.AppointmentRepository
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.BookingRepository
import rs.termin.app.core.domain.repository.ConnectivityMonitor
import rs.termin.app.core.domain.repository.InboxRepository
import rs.termin.app.core.domain.repository.IntegrationRepository
import rs.termin.app.core.domain.repository.LocationProvider
import rs.termin.app.core.domain.repository.PreferencesRepository
import rs.termin.app.core.domain.repository.ReminderScheduler
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.repository.StaffRepository
import rs.termin.app.core.domain.repository.UserRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Singleton

/**
 * Sloj podataka za testove cele aplikacije: isti interfejsi kao u produkciji, ali sve iz memorije
 * (bez Firebase-a i mreže). Test dobija iste instance preko `@Inject` i njima upravlja.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object FakeDataModule {
    @Provides @Singleton fun fakeAuth() = FakeAuthRepository()
    @Provides @Singleton fun fakeUsers() = FakeUserRepository()
    @Provides @Singleton fun fakeSalons() = FakeSalonRepository()
    @Provides @Singleton fun fakeBooking(clock: Clock, salons: FakeSalonRepository) = FakeBookingRepository(clock, salons)
    @Provides @Singleton fun fakeAppointments(booking: FakeBookingRepository) = FakeAppointmentRepository(booking)
    @Provides @Singleton fun fakeInbox() = FakeInboxRepository()
    @Provides @Singleton fun fakeStaff(booking: FakeBookingRepository) = FakeStaffRepository(booking)
    @Provides @Singleton fun fakeIntegrations() = FakeIntegrationRepository()
    @Provides @Singleton fun fakePreferences() = FakePreferences()
    @Provides @Singleton fun fakeLocation() = FakeLocationProvider()
    @Provides @Singleton fun fakeConnectivity() = FakeConnectivity()
    @Provides @Singleton fun fakeReminders() = FakeReminders()

    @Provides fun auth(fake: FakeAuthRepository): AuthRepository = fake
    @Provides fun users(fake: FakeUserRepository): UserRepository = fake
    @Provides fun salons(fake: FakeSalonRepository): SalonRepository = fake
    @Provides fun booking(fake: FakeBookingRepository): BookingRepository = fake
    @Provides fun appointments(fake: FakeAppointmentRepository): AppointmentRepository = fake
    @Provides fun inbox(fake: FakeInboxRepository): InboxRepository = fake
    @Provides fun staff(fake: FakeStaffRepository): StaffRepository = fake
    @Provides fun integrations(fake: FakeIntegrationRepository): IntegrationRepository = fake
    @Provides fun preferences(fake: FakePreferences): PreferencesRepository = fake
    @Provides fun location(fake: FakeLocationProvider): LocationProvider = fake
    @Provides fun connectivity(fake: FakeConnectivity): ConnectivityMonitor = fake
    @Provides fun reminders(fake: FakeReminders): ReminderScheduler = fake
}

/**
 * Nepomerljiv sat (ponedeljak 2. 11. 2026. u 8:00 po beogradskom vremenu) i posao na glavnoj niti,
 * da bi Compose test čekao i njega pre provere ekrana.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [CommonModule::class])
object TestCommonModule {
    val today: LocalDate = LocalDate.of(2026, 11, 2)

    @Provides @IoDispatcher fun ioDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides @DefaultDispatcher fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @Singleton
    @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Provides @Singleton fun clock(): Clock = Clock.fixed(TestData.at(today, 8), ZoneOffset.UTC)
}
