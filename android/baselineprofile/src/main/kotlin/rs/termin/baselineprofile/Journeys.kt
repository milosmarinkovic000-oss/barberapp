package rs.termin.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** Paket aplikacije koja se meri (plugin ga prosleđuje kao instrumentacioni argument). */
val targetPackage: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId") ?: "rs.termin.app"

private const val WAIT_MS = 5_000L

/**
 * Najčešći put pri prvom pokretanju: uvodni ekrani (listanje) i ekran prijave.
 * Prijava traži pravi nalog, pa se profil pravi za deo koji svaki korisnik prolazi.
 */
fun MacrobenchmarkScope.firstRunJourney() {
    device.wait(Until.hasObject(By.pkg(targetPackage).depth(0)), WAIT_MS)
    val pager = device.findObject(By.scrollable(true))
    if (pager != null) {
        repeat(2) {
            pager.swipe(Direction.LEFT, 0.8f)
            device.waitForIdle()
        }
    }
    device.waitForIdle()
}
