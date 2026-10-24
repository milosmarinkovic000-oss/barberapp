package rs.termin.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.test.FakeImageLoaderEngine
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rs.termin.app.MainActivity
import rs.termin.app.R
import rs.termin.app.core.domain.model.AppointmentSource
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.Role
import rs.termin.app.core.domain.model.SalonRules
import rs.termin.app.core.domain.repository.AppPreferences
import rs.termin.app.core.domain.repository.AuthState
import rs.termin.app.core.ui.Formatter
import rs.termin.app.core.ui.TestTags
import rs.termin.app.testing.FakeAppointmentRepository
import rs.termin.app.testing.FakeAuthRepository
import rs.termin.app.testing.FakeBookingRepository
import rs.termin.app.testing.FakeConnectivity
import rs.termin.app.testing.FakePreferences
import rs.termin.app.testing.FakeReminders
import rs.termin.app.testing.FakeStaffRepository
import rs.termin.app.testing.FakeUserRepository
import rs.termin.app.testing.TestCommonModule
import rs.termin.app.testing.TestData
import rs.termin.app.testing.TestProfiles
import rs.termin.app.testing.initTestFirebase
import javax.inject.Inject

/**
 * Prava aplikacija (MainActivity, faze, navigacija, deep linkovi) nad lažnim backendom iz memorije
 * ([rs.termin.app.testing.FakeDataModule]): korisnik prolazi kroz nju kao na telefonu.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "b+sr+Latn-w411dp-h891dp")
class AppJourneyTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var auth: FakeAuthRepository

    @Inject lateinit var users: FakeUserRepository

    @Inject lateinit var preferences: FakePreferences

    @Inject lateinit var booking: FakeBookingRepository

    @Inject lateinit var appointments: FakeAppointmentRepository

    @Inject lateinit var reminders: FakeReminders

    @Inject lateinit var staff: FakeStaffRepository

    @Inject lateinit var connectivity: FakeConnectivity

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = TestCommonModule.today
    private var scenario: ActivityScenario<MainActivity>? = null

    @OptIn(DelicateCoilApi::class)
    @Before
    fun setUp() {
        hilt.inject()
        initTestFirebase(context)
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .coroutineContext(Dispatchers.Unconfined)
                .components { add(FakeImageLoaderEngine.Builder().default(ColorDrawable(PHOTO_COLOR).asImage()).build()) }
                .build(),
        )
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch(link: String? = null) {
        val intent = Intent(context, MainActivity::class.java)
        if (link != null) intent.setAction(Intent.ACTION_VIEW).setData(link.toUri())
        scenario = ActivityScenario.launch(intent)
    }

    private fun text(id: Int) = context.getString(id)

    private fun back() {
        scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    /** Na trenutnom ekranu svako dugme i polje ima naziv koji TalkBack izgovara (tekst ili opis). */
    private fun assertActionsHaveLabels(screen: String) {
        val unlabeled = compose.onAllNodes(hasClickAction() or hasSetTextAction()).fetchSemanticsNodes().filter { node ->
            val texts = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text } +
                node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            texts.all { it.isBlank() }
        }
        assertTrue("$screen: elementi bez naziva za čitač ekrana: ${unlabeled.map { it.config }}", unlabeled.isEmpty())
    }

    /** Element u otvorenom donjem listu (ne u ekranu ispod njega). */
    private fun inSheet(matcher: SemanticsMatcher) = matcher and hasAnyAncestor(isDialog())

    private fun signedInOwner() {
        preferences.state.value = AppPreferences(onboardingDone = true)
        users.flow.value = TestProfiles.customer(name = "Vesna Vlasnik").copy(uid = "owner", roles = setOf(Role.Customer, Role.Owner), salonId = "s1")
        auth.state.value = AuthState.SignedIn("owner", setOf(Role.Customer, Role.Owner), "s1", null)
    }

    private fun signedInCustomer() {
        preferences.state.value = AppPreferences(onboardingDone = true)
        users.flow.value = TestProfiles.customer()
        auth.state.value = AuthState.SignedIn("u1", setOf(Role.Customer), null, null)
    }

    @Test
    fun noviKlijentOdUvodaDoZakazanogTermina() {
        launch()

        // Uvod → prijava brojem telefona → SMS kod → ime.
        compose.onNodeWithTag(TestTags.ONBOARDING_SKIP).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("060 1234567")
        compose.onNodeWithText(text(R.string.login_send_code)).performClick()
        compose.onNodeWithText(text(R.string.login_code_title)).assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("123456")
        compose.onNodeWithText(text(R.string.login_verify)).performClick()

        compose.onNodeWithText(text(R.string.setup_title)).assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("Jelena Petrović")
        compose.onNodeWithText(text(R.string.setup_finish)).performClick()
        assertEquals("Jelena Petrović", users.flow.value?.name)
        assertEquals(1, users.pushRegistrations)

        // Početna → salon → zakazivanje dve usluge kod bilo kog frizera, sutra u 10:30.
        compose.onAllNodesWithText(TestData.salon().name)[0].performClick()
        compose.onNodeWithText(text(R.string.salon_book)).performClick()
        compose.onNodeWithText("Usluga cut").performClick()
        compose.onNodeWithText("Usluga color").performClick()
        compose.onNodeWithText(text(R.string.booking_next)).performClick()
        compose.onNodeWithText(text(R.string.stylist_any)).performClick()
        compose.onNodeWithText(text(R.string.booking_next)).performClick()

        val tomorrow = today.plusDays(1)
        compose.onNode(hasClickAction() and hasContentDescription(Formatter(context).dayLong(tomorrow), substring = true)).performClick()
        compose.onAllNodes(hasClickAction() and hasContentDescription("10:30", substring = true))[0].performClick()
        compose.onNodeWithText(text(R.string.booking_next)).performClick()
        compose.onAllNodesWithText(text(R.string.booking_confirm))[0].performClick()

        compose.onNodeWithText(text(R.string.success_title)).assertExists()
        val appointment = booking.confirmed.single()
        assertEquals(TestData.at(tomorrow, 10, 30), appointment.start)
        assertEquals(listOf("cut", "color"), appointment.services.map { it.id })
        assertEquals(listOf(appointment.id), reminders.scheduled)

        // „Moji termini“ prikazuje novi termin.
        compose.onNodeWithText(text(R.string.success_done)).performScrollTo().performClick()
        compose.onNode(hasText("Usluga cut", substring = true) and hasText("Usluga color", substring = true)).assertExists()
    }

    @Test
    fun otkazivanjeURokuOslobadjaTerminIBriseLokalniPodsetnik() {
        signedInCustomer()
        booking.confirmed = listOf(TestData.appointment("tomorrow", TestData.at(today.plusDays(1), 10)))

        launch()

        compose.onNodeWithText(text(R.string.nav_appointments)).performClick()
        compose.onAllNodesWithText(TestData.salon().name)[0].performClick()
        compose.onNodeWithText(text(R.string.appointment_cancel)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.appointment_cancel_confirm)).performClick()

        compose.onNodeWithText(text(R.string.appointment_canceled)).assertExists()
        assertEquals(AppointmentStatus.CanceledByCustomer, booking.confirmed.single().status)
        assertTrue("tomorrow" in reminders.canceled)
    }

    @Test
    fun kasnoOtkazivanjeJeBlokiranoSaObjasnjenjem() {
        signedInCustomer()
        // Termin je za 6 sati, a salon dozvoljava otkazivanje najkasnije 12 sati ranije.
        booking.confirmed = listOf(TestData.appointment("late", TestData.at(today, 14)))

        launch("termin://appointment/late")

        val explanation = context.getString(R.string.appointment_deadline_text, SalonRules.Default.cancelHours)
        compose.onNodeWithText(explanation).assertExists()
        compose.onNodeWithText(text(R.string.appointment_cancel)).performScrollTo().performClick()
        compose.onNode(hasText(explanation) and hasAnyAncestor(isDialog())).assertExists()
        compose.onNode(hasText(text(R.string.appointment_call_salon)) and hasClickAction() and hasAnyAncestor(isDialog())).assertExists()
        assertEquals(AppointmentStatus.Confirmed, booking.confirmed.single().status)
    }

    @Test
    fun vlasnikVidiKalendarSaTerminomISvojSalon() {
        signedInOwner()
        appointments.mine.value = listOf(TestData.appointment("today", TestData.at(today, 10)))

        launch()

        compose.onNode(hasText(text(R.string.nav_calendar)) and isSelected()).assertExists()
        compose.onAllNodesWithText("Marko Marković", substring = true)[0].assertExists()
        compose.onNodeWithText(text(R.string.nav_salon)).performClick()
        compose.onAllNodesWithText(TestData.salon().name)[0].assertExists()
    }

    @Test
    fun frizerSeBrzoPrijavljujeIVidiSamoSvojeTermine() {
        preferences.state.value = AppPreferences(onboardingDone = true)
        users.flow.value = TestProfiles.customer(name = "Ana Anić").copy(uid = "stylist-a", roles = setOf(Role.Customer, Role.Stylist), salonId = "s1", stylistId = "a")
        booking.confirmed = listOf(
            TestData.appointment("mine", TestData.at(today, 10)),
            TestData.appointment("other", TestData.at(today, 11)).copy(stylistId = "b", stylistName = "Bojan", customerName = "Jovana Jovanović"),
        )

        launch()

        compose.onNodeWithText(text(R.string.login_demo_stylist)).performScrollTo().performClick()

        compose.onNode(hasText(text(R.string.nav_calendar)) and isSelected()).assertExists()
        compose.onAllNodesWithText("Marko Marković", substring = true)[0].assertExists()
        compose.onNodeWithText("Jovana Jovanović", substring = true).assertDoesNotExist()
    }

    @Test
    fun vlasnikRucnoUnosiTerminKojiSeOdmahVidiUKalendaru() {
        signedInOwner()

        launch()

        // Dugme se traži po onome što izgovara TalkBack.
        compose.onNode(hasClickAction() and hasContentDescription(text(R.string.calendar_new))).performClick()
        compose.onNode(inSheet(hasText("Ana"))).performClick()
        compose.onNode(inSheet(hasText("Usluga cut", substring = true))).performClick()
        compose.onNode(inSheet(hasText("10:30"))).performClick()
        compose.onNode(inSheet(hasSetTextAction() and hasText(text(R.string.manual_customer_name)))).performTextInput("Petar Petrović")
        compose.onNode(inSheet(hasText(text(R.string.manual_save)))).performScrollTo().performClick()

        val manual = booking.confirmed.single()
        assertEquals(AppointmentSource.Manual, manual.source)
        assertEquals(TestData.at(today, 10, 30), manual.start)
        assertEquals("a", manual.stylistId)
        compose.onAllNodesWithText("Petar Petrović", substring = true)[0].assertExists()
    }

    @Test
    fun vlasnikBlokiraVremeIBlokadaSeVidiUKalendaru() {
        signedInOwner()

        launch()

        compose.onNode(hasContentDescription(text(R.string.calendar_block))).performClick()
        compose.onNode(inSheet(hasText("Bojan"))).performClick()
        compose.onNode(inSheet(hasSetTextAction() and hasText(text(R.string.block_reason)))).performTextInput("Obuka za balayage")
        compose.onNode(inSheet(hasText(text(R.string.block_save)))).performScrollTo().performClick()
        compose.onNode(inSheet(hasText(text(R.string.block_saved)))).assertExists()
        compose.onNode(inSheet(hasText(text(R.string.action_done)))).performClick()

        val off = staff.timeOff.value.single()
        assertEquals("b", off.stylistId)
        assertEquals(TestData.at(today, 12), off.start)
        assertEquals(TestData.at(today, 13), off.end)
        compose.onNodeWithText("Obuka za balayage").assertExists()
    }

    @Test
    fun pristupacnostSvakiElementKlijentaImaNazivZaCitacEkrana() {
        signedInCustomer()
        booking.confirmed = listOf(
            TestData.appointment("past", TestData.at(today.minusDays(3), 10), status = AppointmentStatus.Completed),
            TestData.appointment("next", TestData.at(today.plusDays(1), 10)),
        )

        launch()

        assertActionsHaveLabels("Početna")
        compose.onNodeWithText(text(R.string.nav_search)).performClick()
        assertActionsHaveLabels("Pretraga")
        compose.onNodeWithText(text(R.string.nav_appointments)).performClick()
        assertActionsHaveLabels("Termini")
        compose.onAllNodesWithText(TestData.salon().name)[0].performClick()
        compose.onNodeWithText(text(R.string.appointment_cancel)).assertExists()
        assertActionsHaveLabels("Detalj termina")
        back()
        compose.onNodeWithText(text(R.string.nav_profile)).performClick()
        assertActionsHaveLabels("Profil")

        compose.onNodeWithText(text(R.string.nav_home)).performClick()
        compose.onAllNodesWithText(TestData.salon().name)[0].performClick()
        assertActionsHaveLabels("Salon")
        compose.onNodeWithText(text(R.string.salon_book)).performClick()
        assertActionsHaveLabels("Zakazivanje: usluge")
        compose.onNodeWithText("Usluga cut").performClick()
        compose.onNodeWithText(text(R.string.booking_next)).performClick()
        assertActionsHaveLabels("Zakazivanje: frizer")
        compose.onNodeWithText(text(R.string.stylist_any)).performClick()
        compose.onNodeWithText(text(R.string.booking_next)).performClick()
        assertActionsHaveLabels("Zakazivanje: vreme")
        compose.onAllNodes(hasClickAction() and hasContentDescription("10:30", substring = true))[0].performClick()
        compose.onNodeWithText(text(R.string.booking_next)).performClick()
        assertActionsHaveLabels("Zakazivanje: potvrda")
    }

    @Test
    fun pristupacnostSvakiElementVlasnikaImaNazivZaCitacEkrana() {
        signedInOwner()
        booking.confirmed = listOf(
            TestData.appointment("today", TestData.at(today, 10)),
            TestData.appointment("done", TestData.at(today.minusDays(1), 11), status = AppointmentStatus.Completed),
        )

        launch()

        assertActionsHaveLabels("Kalendar")
        compose.onNodeWithText(text(R.string.nav_staff_appointments)).performClick()
        assertActionsHaveLabels("Termini salona")
        compose.onNodeWithText(text(R.string.nav_profile)).performClick()
        assertActionsHaveLabels("Profil vlasnika")
        compose.onNodeWithText(text(R.string.nav_salon)).performClick()
        assertActionsHaveLabels("Salon")
        listOf(
            R.string.manage_info,
            R.string.manage_services,
            R.string.manage_team,
            R.string.manage_integrations,
            R.string.manage_stats,
            R.string.manage_reviews,
            R.string.manage_appearance,
        ).forEach { entry ->
            compose.onNodeWithText(text(entry)).performScrollTo().performClick()
            compose.onNode(hasContentDescription(text(R.string.cd_back)) and hasClickAction()).assertExists()
            assertActionsHaveLabels(text(entry))
            back()
        }
    }

    @Test
    fun bezInternetaSePrikazujuSacuvaniPodaciARadnjeSuObjasnjene() {
        signedInCustomer()
        booking.confirmed = listOf(TestData.appointment("next", TestData.at(today.plusDays(1), 10)))
        connectivity.online.value = false

        launch()

        compose.onNodeWithText(text(R.string.state_offline)).assertExists()
        compose.onAllNodesWithText(TestData.salon().name)[0].assertExists()

        compose.onNodeWithText(text(R.string.nav_appointments)).performClick()
        compose.onAllNodesWithText(TestData.salon().name)[0].performClick()
        compose.onNodeWithText(text(R.string.appointment_cancel)).performScrollTo().assertIsNotEnabled()
        back()

        compose.onNodeWithText(text(R.string.nav_home)).performClick()
        compose.onAllNodesWithText(TestData.salon().name)[0].performClick()
        compose.onNodeWithText(text(R.string.salon_book)).performClick()
        compose.onNodeWithText(text(R.string.state_offline_booking)).assertExists()

        // Veza se vratila: traka nestaje bez ikakve radnje korisnika.
        connectivity.online.value = true
        compose.onNodeWithText(text(R.string.state_offline_booking)).assertDoesNotExist()
        compose.onNodeWithText("Usluga cut").performClick()
        compose.onNodeWithText(text(R.string.booking_next)).assertIsEnabled()
    }

    @Test
    fun deljeniLinkSalonaOtvaraSalonPosleUcitavanja() {
        signedInCustomer()

        launch("termin://salon/s1")

        compose.onNode(hasText(TestData.salon().name) and isHeading()).assertExists()
        compose.onNodeWithText(text(R.string.salon_book)).assertExists()
    }

    @Test
    fun odjavaVracaNaPrijavuIBriseLokalnePodsetnike() {
        signedInCustomer()
        appointments.mine.value = listOf(TestData.appointment("next", TestData.at(today.plusDays(1), 10)))

        launch()

        compose.onNodeWithText(text(R.string.nav_profile)).performClick()
        compose.onNodeWithText(text(R.string.profile_sign_out)).performScrollTo().performClick()
        compose.onNode(hasText(text(R.string.profile_sign_out)) and hasAnyAncestor(isDialog())).performClick()

        compose.onNodeWithText(text(R.string.login_title)).assertExists()
        assertEquals(AuthState.SignedOut, auth.state.value)
        assertEquals(listOf("next"), reminders.canceled)
    }

    private companion object {
        const val PHOTO_COLOR = 0xFFB9A58C.toInt()
    }
}
