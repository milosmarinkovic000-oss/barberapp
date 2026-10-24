package rs.termin.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Merenje pokretanja (hladno i toplo) sa i bez baseline profila i glatkoće listanja salona.
 * Cilj: hladno < 1,5 s, toplo < 0,5 s na srednjem telefonu (rezultati u docs/PERFORMANCE.md).
 * Pre pokretanja: `npm run demo` na računaru (emulatori i seed), pa `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithProfile() = startup(StartupMode.COLD, CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun coldStartupWithoutProfile() = startup(StartupMode.COLD, CompilationMode.None())

    @Test
    fun warmStartup() = startup(StartupMode.WARM, CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun salonListScroll() = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            reachHome()
        },
    ) {
        scrollSalons()
    }

    private fun startup(mode: StartupMode, compilation: CompilationMode) = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = mode,
        iterations = ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
