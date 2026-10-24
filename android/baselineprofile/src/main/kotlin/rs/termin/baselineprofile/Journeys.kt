package rs.termin.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** Paket aplikacije koja se meri (plugin ga prosleđuje kao instrumentacioni argument). */
val targetPackage: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId") ?: "rs.termin.app"

private const val WAIT_MS = 10_000L

// Oznake iz rs.termin.app.core.ui.TestTags (vidljive kao resource-id).
private const val ONBOARDING_SKIP = "onboarding_skip"
private const val DEMO_CUSTOMER = "demo_login_customer"
private const val HOME_LIST = "home_list"

/**
 * Do početnog ekrana klijenta: preskače uvod i prijavljuje demo klijenta ako je potrebno.
 * Merni build radi nad lokalnim emulatorima sa seed podacima (`npm run demo` na računaru).
 */
fun MacrobenchmarkScope.reachHome() {
    device.wait(Until.hasObject(By.pkg(targetPackage).depth(0)), WAIT_MS)
    device.findObject(By.res(ONBOARDING_SKIP))?.click()
    device.wait(Until.hasObject(By.res(DEMO_CUSTOMER)), WAIT_MS / 5)
    device.findObject(By.res(DEMO_CUSTOMER))?.click()
    device.wait(Until.hasObject(By.res(HOME_LIST)), WAIT_MS * 3)
}

/** Listanje početne liste salona (kartice sa fotografijama) dole pa gore. */
fun MacrobenchmarkScope.scrollSalons() {
    val list = device.findObject(By.res(HOME_LIST)) ?: return
    list.setGestureMargin(device.displayWidth / 5)
    repeat(2) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
    }
    list.fling(Direction.UP)
    device.waitForIdle()
}
