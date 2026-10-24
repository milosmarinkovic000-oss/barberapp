package rs.termin.app.core.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import rs.termin.app.BuildConfig
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.model.GeoLocation
import java.util.Locale

/** Centri gradova za početni pin kada lokacija još nije izabrana. */
object CityCenters {
    private val centers = mapOf(
        "beograd" to GeoLocation(44.8176, 20.4569),
        "novi sad" to GeoLocation(45.2671, 19.8335),
        "niš" to GeoLocation(43.3209, 21.8958),
        "nis" to GeoLocation(43.3209, 21.8958),
        "kragujevac" to GeoLocation(44.0128, 20.9114),
        "subotica" to GeoLocation(46.1003, 19.6658),
        "zrenjanin" to GeoLocation(45.3816, 20.3686),
        "pančevo" to GeoLocation(44.8708, 20.6403),
        "čačak" to GeoLocation(43.8914, 20.3497),
        "kraljevo" to GeoLocation(43.7258, 20.6897),
        "novi pazar" to GeoLocation(43.1367, 20.5122),
    )

    val Default: GeoLocation = centers.getValue("beograd")

    fun forCity(city: String): GeoLocation? = centers[city.trim().lowercase(Locale.ROOT)]
}

/**
 * Izbor lokacije salona: mapa sa pinom (dodir pomera pin) kada postoji Maps ključ, inače
 * kartica sa koordinatama i dugmetom „Koristi moju lokaciju“.
 */
@Composable
fun LocationPicker(
    location: GeoLocation,
    onLocationChange: (GeoLocation) -> Unit,
    onUseMyLocation: () -> Unit,
    hint: String,
    fallbackHint: String,
    modifier: Modifier = Modifier,
    locating: Boolean = false,
) {
    val context = LocalContext.current
    val colors = TerminTheme.colors
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onUseMyLocation()
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val showMap = BuildConfig.HAS_MAPS_KEY && TerminTheme.features.map
        if (showMap) {
            val target = LatLng(location.latitude, location.longitude)
            val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(target, MAP_ZOOM) }
            LaunchedEffect(target) {
                if (camera.position.target != target) camera.animate(CameraUpdateFactory.newLatLng(target))
            }
            Text(hint, style = TerminTheme.type.callout, color = colors.textSecondary)
            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(TerminTheme.radii.cardShape),
                cameraPositionState = camera,
                uiSettings = MapUiSettings(zoomControlsEnabled = true, mapToolbarEnabled = false),
                onMapClick = { onLocationChange(GeoLocation(it.latitude, it.longitude)) },
            ) {
                Marker(state = rememberUpdatedMarkerState(position = target))
            }
        } else {
            Notice(fallbackHint, icon = TerminIcon.PinDrop)
            Text(
                stringResource(R.string.location_coordinates, "%.5f".format(Locale.ROOT, location.latitude), "%.5f".format(Locale.ROOT, location.longitude)),
                style = TerminTheme.type.callout,
                color = colors.textSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminButton(
                stringResource(R.string.create_salon_use_location),
                { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                variant = ButtonVariant.Tonal,
                icon = TerminIcon.MyLocation,
                loading = locating,
                modifier = Modifier.weight(1f),
            )
            if (!showMap) {
                TerminButton(
                    stringResource(R.string.action_open_maps),
                    { Actions.openInMaps(context, location, "") },
                    variant = ButtonVariant.Text,
                    icon = TerminIcon.Map,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private const val MAP_ZOOM = 15f
