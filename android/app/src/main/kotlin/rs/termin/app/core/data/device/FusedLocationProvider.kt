package rs.termin.app.core.data.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import rs.termin.app.core.domain.model.GeoLocation
import rs.termin.app.core.domain.repository.LocationProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Približna lokacija (coarse), samo kad je potrebna — bez praćenja u pozadini. */
@Singleton
class FusedLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationProvider {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): GeoLocation? {
        if (!hasPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val token = CancellationTokenSource()
        val location = withTimeoutOrNull(TIMEOUT_MS) {
            runCatching { client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token).await() }.getOrNull()
        } ?: runCatching { client.lastLocation.await() }.getOrNull()
        token.cancel()
        return location?.let { GeoLocation(it.latitude, it.longitude) }
    }

    private companion object {
        const val TIMEOUT_MS = 6_000L
    }
}
