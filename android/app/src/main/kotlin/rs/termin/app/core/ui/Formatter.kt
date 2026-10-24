package rs.termin.app.core.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import rs.termin.app.R
import rs.termin.app.core.domain.model.AppointmentStatus
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.ServiceCategory
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Formatiranje cena, datuma, vremena i rastojanja po pravilima jezika aplikacije:
 * „1.200 RSD“, „utorak, 14. maj“, „za 2 dana“. Svi nazivi dolaze iz string resursa.
 */
class Formatter(private val context: Context, private val zone: ZoneId = BelgradeZone) {
    private val res get() = context.resources
    private val weekdays: Array<String> by lazy { res.getStringArray(R.array.weekday_names) }
    private val weekdaysShort: Array<String> by lazy { res.getStringArray(R.array.weekday_short) }
    private val months: Array<String> by lazy { res.getStringArray(R.array.month_names) }

    fun number(value: Int): String {
        val separator = res.getString(R.string.format_thousands_separator)
        val digits = kotlin.math.abs(value).toString()
        val grouped = digits.reversed().chunked(3).joinToString(separator).reversed()
        return if (value < 0) "-$grouped" else grouped
    }

    fun price(value: Int): String = res.getString(R.string.format_price, number(value))

    fun decimal(value: Double, digits: Int = 1): String {
        val text = "%.${digits}f".format(java.util.Locale.ROOT, value)
        return text.replace(".", res.getString(R.string.format_decimal_separator))
    }

    fun rating(value: Double): String = decimal(value, 1)

    fun dayLong(date: LocalDate): String =
        res.getString(R.string.format_day_long, weekdays[date.dayOfWeek.value - 1], date.dayOfMonth, months[date.monthValue - 1])

    fun dayShort(date: LocalDate): String = res.getString(R.string.format_day_short, weekdaysShort[date.dayOfWeek.value - 1], date.dayOfMonth)

    fun weekdayShort(date: LocalDate): String = weekdaysShort[date.dayOfWeek.value - 1]

    fun dayMonth(date: LocalDate): String = res.getString(R.string.format_day_month, date.dayOfMonth, months[date.monthValue - 1])

    fun monthName(date: LocalDate): String = months[date.monthValue - 1]

    fun time(instant: Instant): String = time(instant.atZone(zone).toLocalTime())

    fun time(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    fun timeRange(start: Instant, end: Instant): String = res.getString(R.string.format_time_range, time(start), time(end))

    fun date(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    fun dateTime(instant: Instant): String = res.getString(R.string.format_date_time, dayLong(date(instant)), time(instant))

    /** „Danas“, „Sutra“ ili „utorak, 14. maj“. */
    fun relativeDay(date: LocalDate, today: LocalDate): String = when (date) {
        today -> res.getString(R.string.format_today)
        today.plusDays(1) -> res.getString(R.string.format_tomorrow)
        else -> dayLong(date)
    }

    /** Kratak opis kada počinje termin: „danas u 14:30“, „sutra u 10:00“, „za 2 dana“. */
    fun countdown(start: Instant, now: Instant): String {
        val minutes = Duration.between(now, start).toMinutes()
        if (minutes < 1) return res.getString(R.string.format_in_minutes_now)
        if (minutes < 60) return res.getQuantityString(R.plurals.countdown_minutes, minutes.toInt(), minutes.toInt())
        val today = now.atZone(zone).toLocalDate()
        val day = start.atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, day).toInt()
        return when {
            days == 0 && minutes < 180 -> {
                val hours = (minutes / 60).toInt()
                res.getQuantityString(R.plurals.countdown_hours, hours, hours)
            }
            days == 0 -> res.getString(R.string.format_today_at, time(start))
            days == 1 -> res.getString(R.string.format_tomorrow_at, time(start))
            else -> res.getQuantityString(R.plurals.countdown_days, days, days)
        }
    }

    fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> res.getString(R.string.format_duration_minutes, m)
            m == 0 -> res.getString(R.string.format_duration_hours, h)
            else -> res.getString(R.string.format_duration_hours_minutes, h, m)
        }
    }

    fun distance(km: Double): String =
        if (km < 1.0) res.getString(R.string.format_distance_m, ((km * 1000) / 10).roundToInt() * 10)
        else res.getString(R.string.format_distance_km, decimal(km, 1))

    fun reviews(count: Int): String = res.getQuantityString(R.plurals.reviews_count, count, count)

    fun category(category: ServiceCategory): String = res.getString(
        when (category) {
            ServiceCategory.Haircut -> R.string.category_haircut
            ServiceCategory.Blowdry -> R.string.category_blowdry
            ServiceCategory.Coloring -> R.string.category_coloring
            ServiceCategory.Highlights -> R.string.category_highlights
            ServiceCategory.Beard -> R.string.category_beard
            ServiceCategory.Treatment -> R.string.category_treatment
        },
    )

    fun status(status: AppointmentStatus): String = res.getString(
        when (status) {
            AppointmentStatus.Held -> R.string.status_held
            AppointmentStatus.Confirmed -> R.string.status_confirmed
            AppointmentStatus.CanceledByCustomer -> R.string.status_canceled_by_customer
            AppointmentStatus.CanceledBySalon -> R.string.status_canceled_by_salon
            AppointmentStatus.Completed -> R.string.status_completed
            AppointmentStatus.NoShow -> R.string.status_no_show
        },
    )
}

val LocalFormatter = staticCompositionLocalOf<Formatter> { error("Formatter nije postavljen") }

/** Formatter za trenutni jezik (LocalContext prati promenu jezika). */
val formatter: Formatter
    @Composable @ReadOnlyComposable get() = LocalFormatter.current

@Composable
fun rememberFormatter(): Formatter {
    val context = LocalContext.current
    return androidx.compose.runtime.remember(context) { Formatter(context) }
}
