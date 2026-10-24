package rs.termin.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rs.termin.app.MainActivity
import rs.termin.app.R
import rs.termin.app.core.designsystem.Appearance
import rs.termin.app.core.designsystem.ThemeMode
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.Role
import rs.termin.app.core.domain.repository.AppPreferences
import rs.termin.app.core.domain.repository.AuthState
import rs.termin.app.core.ui.Formatter
import rs.termin.app.testing.DemoData
import rs.termin.app.testing.FakeAuthRepository
import rs.termin.app.testing.FakeBookingRepository
import rs.termin.app.testing.FakePreferences
import rs.termin.app.testing.FakeSalonRepository
import rs.termin.app.testing.FakeUserRepository
import rs.termin.app.testing.TestCommonModule
import rs.termin.app.testing.TestProfiles
import rs.termin.app.testing.initTestFirebase
import java.io.File
import javax.inject.Inject

/** Koren repozitorijuma (testovi se pokreću iz `android/app`). */
private val repoRoot: File = File(System.getProperty("user.dir")!!).absoluteFile.parentFile!!.parentFile!!

/** `ValueAnimator.setDurationScale` je skriveni test API platforme (isto radi podešavanje „Trajanje animacija“). */
private fun setAnimatorDurationScale(scale: Float) {
    ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
}

/** Fotografije iz `seed/images` umesto Cloud Storage-a (iste kao u demo režimu). */
private class SeedPhotos : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        // salons/<salon>/photos/<ime>.webp → seed/images/<salon>/<ime>.webp; frizeri zadržavaju podfolder.
        val path = Uri.decode(chain.request.data.toString().substringAfter("/o/").substringBefore("?"))
        val parts = path.removePrefix("salons/").split("/").filter { it != "photos" }
        val bitmap = BitmapFactory.decodeFile(File(repoRoot, "seed/images/${parts.joinToString("/")}").path)
        // Kao iz keša u memoriji: bez pretapanja, pa je slika odmah cela.
        return SuccessResult(bitmap.asImage(), chain.request, DataSource.MEMORY_CACHE)
    }
}

/**
 * Snimci prave aplikacije sa demo salonima za javni sajt (`hosting/public/screens`). Proveravaju se
 * kao ostali snimci (`verifyRoborazziDebug`), pa sajt uvek prikazuje trenutni izgled; posle namerne
 * izmene izgleda: `./gradlew recordRoborazziDebug`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "b+sr+Latn-w412dp-h915dp-port-xxhdpi")
class WebsiteScreenshotsTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var auth: FakeAuthRepository

    @Inject lateinit var users: FakeUserRepository

    @Inject lateinit var preferences: FakePreferences

    @Inject lateinit var salons: FakeSalonRepository

    @Inject lateinit var booking: FakeBookingRepository

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = TestCommonModule.today
    private var scenario: ActivityScenario<MainActivity>? = null

    @OptIn(DelicateCoilApi::class)
    @Before
    fun setUp() {
        hilt.inject()
        initTestFirebase(context)
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context).coroutineContext(Dispatchers.Unconfined).components { add(SeedPhotos()) }.build(),
        )
        DemoData.applyTo(salons)
        // Talasić posle dodira je animacija platforme, a ne Compose-a, i pod Robolectric-om se ne završava
        // predvidivo; bez trajanja odmah dolazi do kraja, pa je snimak uvek isti.
        setAnimatorDurationScale(0f)
    }

    @After
    fun tearDown() {
        scenario?.close()
        setAnimatorDurationScale(1f)
    }

    private fun text(id: Int) = context.getString(id)

    private fun launch(dark: Boolean = false) {
        val mode = if (dark) ThemeMode.Dark else ThemeMode.Light
        preferences.state.value = AppPreferences(onboardingDone = true, appearance = Appearance(themeMode = mode))
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        captureScreenRoboImage(
            File(repoRoot, "hosting/public/screens/$name.png").path,
            RoborazziOptions(
                recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            ),
        )
    }

    private fun signedInCustomer() {
        users.flow.value = TestProfiles.customer(name = DemoData.CUSTOMER)
        auth.state.value = AuthState.SignedIn("u1", setOf(Role.Customer), null, null)
    }

    @Test
    fun klijentZakazujeTermin() {
        signedInCustomer()

        launch()

        shot("pocetna")
        compose.onAllNodesWithText(DemoData.lana.name)[0].performClick()
        shot("salon")
        compose.onNodeWithText(text(R.string.salon_book)).performClick()
        compose.onNodeWithText("Žensko šišanje").performClick()
        compose.onNodeWithText("Dubinska hidratacija").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.booking_next)).performClick()
        shot("zakazivanje-frizer")
        compose.onAllNodesWithText("Ivana Pavlović")[0].performClick()
        compose.onNodeWithText(text(R.string.booking_next)).performClick()
        compose.onNode(hasClickAction() and hasContentDescription(Formatter(context).dayLong(today.plusDays(1)), substring = true)).performClick()
        compose.onAllNodes(hasClickAction() and hasContentDescription("10:30", substring = true))[0].performClick()
        shot("zakazivanje-vreme")
        compose.onNodeWithText(text(R.string.booking_next)).performClick()
        shot("zakazivanje-potvrda")
        compose.onAllNodesWithText(text(R.string.booking_confirm))[0].performClick()
        shot("termin-potvrdjen")
        compose.onNodeWithText(text(R.string.success_done)).performScrollTo().performClick()
        compose.onAllNodesWithText(DemoData.lana.name)[0].performClick()
        shot("detalj-termina")
    }

    @Test
    fun salonUTamnojTemi() {
        signedInCustomer()

        launch(dark = true)

        compose.onAllNodesWithText(DemoData.lana.name)[0].performClick()
        shot("salon-tamna-tema")
    }

    @Test
    fun vlasnikVodiSalon() {
        users.flow.value = TestProfiles.customer(name = DemoData.OWNER)
            .copy(uid = "owner", roles = setOf(Role.Customer, Role.Owner), salonId = DemoData.LANA)
        auth.state.value = AuthState.SignedIn("owner", setOf(Role.Customer, Role.Owner), DemoData.LANA, null)
        val stylistIds = DemoData.stylists.map { it.id }
        val serviceIds = listOf("musko-sisanje", "feniranje", "brada", "bojenje")
        // Prethodne dve nedelje (za statistiku) i današnji raspored (za kalendar).
        val past = (1..PAST_DAYS).flatMap { back ->
            (0 until BUSIEST_DAY - back % 4).map { i ->
                DemoData.appointment(
                    "p$back-$i", today.minusDays(back.toLong()), 9 + i * 2, 0, stylistIds[i % stylistIds.size],
                    "Klijent", listOf(serviceIds[i % serviceIds.size]), AppointmentStatus.Completed,
                )
            }
        }
        booking.confirmed = past + listOf(
            DemoData.appointment("t1", today, 9, 0, "stefan", "Nikola Petrović", listOf("musko-sisanje", "brada")),
            DemoData.appointment("t2", today, 9, 30, "marko", "Milan Popović", listOf("musko-sisanje")),
            DemoData.appointment("t3", today, 10, 0, "nina", "Teodora Lukić", listOf("feniranje")),
            DemoData.appointment("t4", today, 11, 0, "stefan", "Dušan Stanković", listOf("zensko-sisanje")),
            DemoData.appointment("t5", today, 11, 30, "nina", "Katarina Simić", listOf("keratin")),
            DemoData.appointment("t6", today, 12, 0, "marko", "Ana Đurić", listOf("brada")),
            DemoData.appointment("t7", today, 15, 0, "stefan", DemoData.CUSTOMER, listOf("zensko-sisanje", "hidratacija")),
        )

        launch()

        shot("kalendar")
        compose.onNodeWithText(text(R.string.nav_staff_appointments)).performClick()
        shot("termini-salona")
        compose.onNodeWithText(text(R.string.nav_salon)).performClick()
        compose.onNodeWithText(text(R.string.manage_stats)).performScrollTo().performClick()
        compose.onNode(hasClickAction() and hasContentDescription(text(R.string.calendar_prev))).performClick()
        shot("statistika")
    }

    private companion object {
        const val PAST_DAYS = 13
        const val BUSIEST_DAY = 6
    }
}
