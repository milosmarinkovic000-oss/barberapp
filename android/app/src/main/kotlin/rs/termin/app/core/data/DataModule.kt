package rs.termin.app.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import rs.termin.app.core.data.appointments.FirestoreAppointmentRepository
import rs.termin.app.core.data.appointments.FirestoreInboxRepository
import rs.termin.app.core.data.auth.FirebaseAuthRepository
import rs.termin.app.core.data.booking.FunctionsBookingRepository
import rs.termin.app.core.data.device.AndroidConnectivityMonitor
import rs.termin.app.core.data.device.FusedLocationProvider
import rs.termin.app.core.data.integrations.FunctionsIntegrationRepository
import rs.termin.app.core.data.notifications.WorkManagerReminderScheduler
import rs.termin.app.core.data.prefs.DataStorePreferencesRepository
import rs.termin.app.core.data.salon.FirestoreSalonRepository
import rs.termin.app.core.data.staff.FirebaseStaffRepository
import rs.termin.app.core.data.user.FirebaseUserRepository
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds @Singleton abstract fun auth(impl: FirebaseAuthRepository): AuthRepository
    @Binds @Singleton abstract fun users(impl: FirebaseUserRepository): UserRepository
    @Binds @Singleton abstract fun salons(impl: FirestoreSalonRepository): SalonRepository
    @Binds @Singleton abstract fun booking(impl: FunctionsBookingRepository): BookingRepository
    @Binds @Singleton abstract fun appointments(impl: FirestoreAppointmentRepository): AppointmentRepository
    @Binds @Singleton abstract fun inbox(impl: FirestoreInboxRepository): InboxRepository
    @Binds @Singleton abstract fun staff(impl: FirebaseStaffRepository): StaffRepository
    @Binds @Singleton abstract fun integrations(impl: FunctionsIntegrationRepository): IntegrationRepository
    @Binds @Singleton abstract fun preferences(impl: DataStorePreferencesRepository): PreferencesRepository
    @Binds @Singleton abstract fun location(impl: FusedLocationProvider): LocationProvider
    @Binds @Singleton abstract fun connectivity(impl: AndroidConnectivityMonitor): ConnectivityMonitor
    @Binds @Singleton abstract fun reminders(impl: WorkManagerReminderScheduler): ReminderScheduler
}
