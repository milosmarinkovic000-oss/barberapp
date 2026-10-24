package rs.termin.app.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass
import rs.termin.app.BuildConfig
import rs.termin.app.R
import rs.termin.app.core.domain.model.AuthError
import rs.termin.app.core.domain.model.BookingError
import rs.termin.app.core.domain.model.GeoLocation
import rs.termin.app.core.domain.model.PolicyReason
import java.time.Instant

/** Poruka za grešku sa servera ili mreže (jasna poruka na srpskom, odnosno engleskom). */
@StringRes
fun Throwable.messageRes(): Int = when (this) {
    is BookingError.SlotTaken -> R.string.error_slot_taken
    is BookingError.PolicyViolation -> when (reason) {
        PolicyReason.CancelDeadline -> R.string.error_cancel_deadline
        PolicyReason.OutsideWindow -> R.string.error_outside_window
        PolicyReason.MaxHolds -> R.string.error_max_holds
        PolicyReason.MaxWaitlist -> R.string.error_max_waitlist
        PolicyReason.NotActive -> R.string.error_not_active
        PolicyReason.NotCompleted -> R.string.error_not_completed
        PolicyReason.AlreadyReviewed -> R.string.error_already_reviewed
        PolicyReason.NotStarted -> R.string.error_not_started
        PolicyReason.HoldExpired -> R.string.error_hold_expired
        PolicyReason.AlreadyInvited -> R.string.error_already_invited
        PolicyReason.NotConnected -> R.string.error_not_connected
        PolicyReason.Other -> R.string.error_unknown
    }
    BookingError.NotAllowed -> R.string.error_not_allowed
    BookingError.Validation -> R.string.error_validation
    BookingError.NotFound -> R.string.error_not_found
    BookingError.RateLimited -> R.string.error_rate_limited
    BookingError.IntegrationNotConfigured -> R.string.error_integration_not_configured
    BookingError.Offline -> R.string.error_offline
    is AuthError.InvalidPhone -> R.string.auth_error_invalid_phone
    is AuthError.InvalidCode -> R.string.auth_error_invalid_code
    is AuthError.CodeExpired -> R.string.auth_error_code_expired
    is AuthError.TooManyRequests -> R.string.auth_error_too_many
    is AuthError.Network -> R.string.error_offline
    is AuthError.GoogleUnavailable -> R.string.auth_error_google
    is AuthError.Cancelled -> R.string.auth_error_cancelled
    is java.io.IOException -> R.string.error_offline
    else -> R.string.error_unknown
}

/** Širok ekran (tablet, otvoren preklopni telefon, položen telefon): lista i detalji jedno pored drugog. */
@Composable
fun isWideLayout(): Boolean =
    currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

/** Srednja širina (preklopni telefon, manji tablet uspravno): navigation rail umesto donje trake. */
@Composable
fun isMediumOrWider(): Boolean =
    currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

/** Sistemske radnje: poziv, put do salona, deljenje, kalendar, Custom Tab. */
object Actions {
    fun call(context: Context, phone: String) {
        start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
    }

    /** Navigacija u Google Mapama (ili bilo kojoj aplikaciji za mape). */
    fun directions(context: Context, location: GeoLocation?, address: String) {
        val destination = location?.let { "${it.latitude},${it.longitude}" } ?: Uri.encode(address)
        val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$destination")
        start(context, Intent(Intent.ACTION_VIEW, uri))
    }

    fun openInMaps(context: Context, location: GeoLocation?, label: String) {
        val query = location?.let { "${it.latitude},${it.longitude}(${Uri.encode(label)})" } ?: Uri.encode(label)
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query")))
    }

    fun share(context: Context, text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        start(context, Intent.createChooser(send, title))
    }

    /** „Dodaj u kalendar“ preko CalendarContract (bez dozvole za kalendar). */
    fun addToCalendar(context: Context, title: String, start: Instant, end: Instant, location: String, description: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toEpochMilli())
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.toEpochMilli())
            putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
        }
        start(context, intent)
    }

    fun openCustomTab(context: Context, url: String) {
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
        } catch (e: ActivityNotFoundException) {
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    fun salonLink(salonId: String): String = "https://${BuildConfig.APP_LINK_HOST}/s/$salonId"

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            android.widget.Toast.makeText(context, R.string.error_no_app, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
