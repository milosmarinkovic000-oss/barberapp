package rs.termin.app.core.designsystem

import androidx.compose.runtime.Immutable

/**
 * Svi izbori iz odeljka 0 („Moj izbor“) na jednom mestu. Promena jedne vrednosti u
 * [ThemeConfig.Default] menja izgled cele aplikacije, bez izmena po ekranima.
 *
 * Naziv aplikacije je u `res/values/strings.xml` (app_name), paket u `app/build.gradle.kts`,
 * a grad za demo podatke u `seed/src/data.ts`.
 */
@Immutable
data class ThemeConfig(
    val style: StyleChoice = StyleChoice.LuxuryMinimal,
    val palette: PaletteChoice = PaletteChoice.Champagne,
    /** Samo za [PaletteChoice.Custom]: akcentna boja kao 0xRRGGBB. */
    val customAccent: Long = 0xB08D57,
    val typography: TypographyChoice = TypographyChoice.FrauncesInter,
    val roundness: Roundness = Roundness.Moderate,
    val density: Density = Density.Airy,
    val motion: MotionLevel = MotionLevel.Subtle,
    val iconStyle: IconStyle = IconStyle.Outlined,
    val salonCard: SalonCardStyle = SalonCardStyle.LargePhoto,
    val navigation: NavigationStyle = NavigationStyle.BottomBar,
    val initialThemeMode: ThemeMode = ThemeMode.System,
    val features: Features = Features(),
) {
    companion object {
        /** Izbor iz odeljka 0. U release buildu važi samo ova vrednost. */
        val Default = ThemeConfig()
    }
}

/** Funkcije iz odeljka 0. Isključena funkcija se ne prikazuje nigde u interfejsu. */
@Immutable
data class Features(
    val phoneLogin: Boolean = true,
    val googleLogin: Boolean = true,
    val map: Boolean = true,
    val waitlist: Boolean = true,
    val reviews: Boolean = true,
    val favorites: Boolean = true,
    val ownerStats: Boolean = true,
    val googleCalendar: Boolean = true,
    val calendly: Boolean = true,
    val calcom: Boolean = true,
    val english: Boolean = true,
    val inAppAppearance: Boolean = true,
    val salonBranding: Boolean = true,
) {
    val anyCalendarIntegration: Boolean get() = googleCalendar || calendly || calcom
}

/** Stilovi A–F. Razlikuju se po tipografskoj skali, zaobljenjima, senkama, ivicama i obradi fotografija. */
enum class StyleChoice {
    /** A. Luksuzni minimal: puno praznog prostora, serif naslovi, fini akcenti. */
    LuxuryMinimal,

    /** B. Moderni čist: sans tipografija, neutralne boje, jak kontrast, precizne linije. */
    ModernClean,

    /** C. Editorial: fotografije preko celog ekrana, krupni naslovi. */
    Editorial,

    /** D. Tamni noir: tamna osnova, metalni akcenat, dramatične fotografije. */
    DarkNoir,

    /** E. Nežni pastel: svetle pastelne površine, mekani oblici. */
    SoftPastel,

    /** F. Staklo: prozirne zamućene površine preko fotografija. */
    Glass,
}

enum class PaletteChoice {
    Champagne,
    Emerald,
    Bordeaux,
    MidnightBlue,
    GraphiteCoral,

    /** Boje telefona (Material You) na Android-u 12+, inače Šampanj. */
    DeviceDynamic,
    Custom,
}

enum class TypographyChoice { FrauncesInter, PlayfairManrope, DmSerifDmSans, InterOnly, Poppins }

enum class Roundness { Sharp, Moderate, Round }

enum class Density { Airy, Compact }

enum class MotionLevel { Minimal, Subtle, Expressive }

enum class IconStyle { Outlined, Rounded, Sharp }

enum class SalonCardStyle { LargePhoto, CompactInfo }

enum class NavigationStyle { BottomBar, BottomBarWithBookFab }

enum class ThemeMode { System, Light, Dark }
