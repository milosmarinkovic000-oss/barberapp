package rs.termin.app.ui

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.test.FakeImageLoaderEngine
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rs.termin.app.core.designsystem.StyleChoice
import rs.termin.app.core.designsystem.ThemeConfig
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.Role
import rs.termin.app.core.domain.model.TimeOff
import rs.termin.app.core.domain.model.TimeOffKind
import rs.termin.app.core.domain.repository.AuthState
import rs.termin.app.core.domain.usecase.ConfirmBookingUseCase
import rs.termin.app.core.domain.usecase.GetAvailabilityUseCase
import rs.termin.app.core.domain.usecase.HoldSlotUseCase
import rs.termin.app.core.domain.usecase.RescheduleUseCase
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.isWideLayout
import rs.termin.app.feature.appointments.AppointmentsContent
import rs.termin.app.feature.appointments.AppointmentsLists
import rs.termin.app.feature.appointments.AppointmentsUiState
import rs.termin.app.feature.auth.LoginContent
import rs.termin.app.feature.auth.LoginUiState
import rs.termin.app.feature.booking.BookingScreen
import rs.termin.app.feature.booking.BookingViewModel
import rs.termin.app.feature.calendar.CalendarScreen
import rs.termin.app.feature.calendar.CalendarViewModel
import rs.termin.app.feature.home.HomeContent
import rs.termin.app.feature.home.HomeUiState
import rs.termin.app.feature.home.NearbyState
import rs.termin.app.feature.salon.SalonScreen
import rs.termin.app.feature.salon.SalonViewModel
import rs.termin.app.testing.FakeAppointmentRepository
import rs.termin.app.testing.FakeAuthRepository
import rs.termin.app.testing.FakeBookingRepository
import rs.termin.app.testing.FakeConnectivity
import rs.termin.app.testing.FakePreferences
import rs.termin.app.testing.FakeReminders
import rs.termin.app.testing.FakeSalonRepository
import rs.termin.app.testing.FakeStaffRepository
import rs.termin.app.testing.FakeUserRepository
import rs.termin.app.testing.TestApp
import rs.termin.app.testing.TestData
import rs.termin.app.testing.TestProfiles
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Uređaj, tema, veličina slova i stil za jedan snimak. */
data class Variant(val name: String, val qualifiers: String, val dark: Boolean = false, val fontScale: Float = 1f, val style: StyleChoice = StyleChoice.LuxuryMinimal) {
    override fun toString() = name
}

/**
 * Snimci ekrana (Roborazzi): mali, veliki i preklopni telefon i tablet; svetla i tamna tema;
 * slova 100 % i 200 %; podrazumevani stil i još dva. Snimanje: `./gradlew recordRoborazziDebug`,
 * provera u CI: `./gradlew verifyRoborazziDebug`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "b+sr+Latn")
class ScreenshotTest(private val variant: Variant) {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 11, 2)
    private val now = TestData.at(today, 8)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @OptIn(DelicateCoilApi::class)
    @Before
    fun setUp() {
        RuntimeEnvironment.setQualifiers("+${variant.qualifiers}")
        RuntimeEnvironment.setFontScale(variant.fontScale)
        // Fotografije su u snimcima jednobojne (bez mreže, uvek isti rezultat).
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .coroutineContext(Dispatchers.Unconfined)
                .components { add(FakeImageLoaderEngine.Builder().default(ColorDrawable(PHOTO_COLOR).asImage()).build()) }
                .build(),
        )
    }

    private fun snap(screen: String, content: @Composable () -> Unit) {
        compose.setContent { TestApp(ThemeConfig.Default.copy(style = variant.style), dark = variant.dark) { content() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/${screen}_${variant.name}.png",
            roborazziOptions = RoborazziOptions(
                recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
                // Do 1 % piksela razlike (anti-aliasing na drugoj mašini) ne smatra se promenom izgleda.
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            ),
        )
    }

    @Test
    fun login() = snap("login") {
        LoginContent(
            state = LoginUiState(phoneInput = "60 123 4567", googleAvailable = true, demoAvailable = false),
            onPhoneChange = {}, onSendCode = {}, onCodeChange = {}, onVerify = {}, onChangeNumber = {},
            onGoogle = {}, onGoogleDemo = {}, onDismissGoogleDemo = {}, onDemo = {}, onOpenPrivacy = {},
        )
    }

    @Test
    fun home() = snap("home") {
        val salons = listOf(
            TestData.salon("s1"),
            TestData.salon("s2").copy(name = "Barbershop Dorćol", address = "Cara Dušana 21", priceLevel = 1),
            TestData.salon("s3").copy(name = "Atelje Kosa", address = "Bulevar kralja Aleksandra 73", priceLevel = 3),
        )
        HomeContent(
            state = HomeUiState(
                firstName = "Marko",
                freeToday = Loadable.Ready(salons.take(2).map { it.copy(nextFreeAt = TestData.at(today, 11)) }),
                nearby = NearbyState.NeedsPermission,
                salons = Loadable.Ready(salons),
                now = now,
            ),
            contentPadding = PaddingValues(),
            onOpenSalon = {}, onSearch = { _, _ -> }, onRequestLocation = {}, onRetry = {}, onLoadMore = {}, onToggleFavorite = {},
        )
    }

    @Test
    fun bookingTime() {
        val salons = FakeSalonRepository()
        val booking = FakeBookingRepository(clock, salons)
        val reminders = FakeReminders()
        val vm = BookingViewModel(
            savedStateHandle = SavedStateHandle(mapOf("salonId" to "s1", "services" to "cut", "stylist" to "a", "date" to null, "rescheduleId" to null)),
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
        snap("booking_time") { BookingScreen(onClose = {}, onCompleted = {}, viewModel = vm) }
    }

    @Test
    fun appointments() = snap("appointments") {
        val upcoming = listOf(
            TestData.appointment("a1", TestData.at(today, 10, 30)),
            TestData.appointment("a2", TestData.at(today.plusDays(4), 17), durationMin = 90),
        )
        val past = listOf(TestData.appointment("p1", TestData.at(today.minusDays(20), 12), status = AppointmentStatus.Completed))
        AppointmentsContent(
            state = AppointmentsUiState(lists = Loadable.Ready(AppointmentsLists(upcoming, past, emptyList())), now = now),
            wide = isWideLayout(),
            contentPadding = PaddingValues(),
            onShowPast = {}, onOpen = {}, onFindSalon = {}, onRetry = {}, onLeaveWaitlist = {}, onBookFromWaitlist = { _, _ -> },
            detailPane = { id -> Text(id) },
        )
    }

    @Test
    fun salon() {
        val salons = FakeSalonRepository()
        val vm = SalonViewModel("s1", salons, FakeUserRepository(TestProfiles.customer()), clock)
        snap("salon") { SalonScreen("s1", onBack = {}, onBook = { _, _ -> }, viewModel = vm) }
    }

    @Test
    fun ownerCalendar() {
        val salons = FakeSalonRepository(
            stylists = listOf(TestData.stylist("a", "Ana Petrović"), TestData.stylist("b", "Bojan Ilić"), TestData.stylist("c", "Vesna Kostić")),
        )
        val appointments = FakeAppointmentRepository()
        appointments.mine.value = listOf(
            TestData.appointment("c1", TestData.at(today, 9), durationMin = 60),
            TestData.appointment("c2", TestData.at(today, 11, 30), durationMin = 90).copy(stylistId = "b", customerName = "Jelena Jovanović"),
            TestData.appointment("c3", TestData.at(today, 14), durationMin = 30, status = AppointmentStatus.Held)
                .copy(stylistId = "c", customerName = "Nikola Nikolić", holdExpiresAt = now.plusSeconds(240)),
        )
        val staff = FakeStaffRepository()
        staff.timeOff.value = listOf(TimeOff("t1", "a", TestData.at(today, 13), TestData.at(today, 14), TimeOffKind.Block, "Pauza"))
        val vm = CalendarViewModel(
            auth = FakeAuthRepository(AuthState.SignedIn("owner", setOf(Role.Owner), "s1", null)),
            salons = salons,
            appointments = appointments,
            staff = staff,
            connectivity = FakeConnectivity(),
            clock = clock,
        )
        snap("calendar") { CalendarScreen(contentPadding = PaddingValues(), onOpenAppointment = {}, onOpenTeam = {}, viewModel = vm) }
    }

    companion object {
        private const val PHOTO_COLOR = 0xFFB9A88F.toInt()

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<Array<Any>> = listOf(
            Variant("small_light", "w360dp-h640dp-port-xhdpi"),
            Variant("small_dark", "w360dp-h640dp-port-xhdpi", dark = true),
            Variant("small_font200", "w360dp-h640dp-port-xhdpi", fontScale = 2f),
            Variant("large_light", "w412dp-h915dp-port-xxhdpi"),
            Variant("large_dark", "w412dp-h915dp-port-xxhdpi", dark = true),
            Variant("large_font200_dark", "w412dp-h915dp-port-xxhdpi", dark = true, fontScale = 2f),
            Variant("foldable_light", "w673dp-h841dp-port-xxhdpi"),
            Variant("foldable_dark", "w673dp-h841dp-port-xxhdpi", dark = true),
            Variant("tablet_light", "w1280dp-h800dp-land-xhdpi"),
            Variant("tablet_dark", "w1280dp-h800dp-land-xhdpi", dark = true),
            Variant("large_modern", "w412dp-h915dp-port-xxhdpi", style = StyleChoice.ModernClean),
            Variant("large_noir", "w412dp-h915dp-port-xxhdpi", style = StyleChoice.DarkNoir),
            Variant("large_pastel", "w412dp-h915dp-port-xxhdpi", style = StyleChoice.SoftPastel),
        ).map { arrayOf(it) }
    }
}
