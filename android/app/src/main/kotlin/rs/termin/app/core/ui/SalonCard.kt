package rs.termin.app.core.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import rs.termin.app.R
import rs.termin.app.core.designsystem.SalonCardStyle
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.RatingSummary
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminIconButton
import rs.termin.app.core.designsystem.component.TerminPhoto
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.domain.model.Salon
import java.time.Instant

/** Shared element prelaz (kartica salona → stranica salona). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedSalonPhoto(salonId: String): Modifier {
    val shared = LocalSharedTransitionScope.current
    val animated = LocalNavAnimatedScope.current
    if (shared == null || animated == null || !TerminTheme.motion.richEffects) return this
    return with(shared) {
        this@sharedSalonPhoto.sharedElement(rememberSharedContentState("salon-photo-$salonId"), animated)
    }
}

/**
 * Kartica salona sa prvim slobodnim terminom. Izgled (velika fotografija ili kompaktna)
 * dolazi iz izbora u ThemeConfig.
 */
@Composable
fun SalonCard(
    salon: Salon,
    photoUrl: String?,
    distanceKm: Double?,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
) {
    val style = TerminTheme.config.salonCard
    if (style == SalonCardStyle.CompactInfo) {
        CompactSalonCard(salon, photoUrl, distanceKm, now, onClick, modifier, favorite, onToggleFavorite)
    } else {
        LargeSalonCard(salon, photoUrl, distanceKm, now, onClick, modifier, favorite, onToggleFavorite)
    }
}

@Composable
private fun LargeSalonCard(
    salon: Salon,
    photoUrl: String?,
    distanceKm: Double?,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier,
    favorite: Boolean?,
    onToggleFavorite: (() -> Unit)?,
) {
    val colors = TerminTheme.colors
    TerminCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Box {
            TerminPhoto(
                url = photoUrl,
                contentDescription = stringResource(R.string.cd_photo_of, salon.name),
                placeholderLabel = salon.name,
                scrim = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(TerminTheme.surfaces.photoAspect.coerceAtLeast(1.2f))
                    .sharedSalonPhoto(salon.id),
            )
            if (favorite != null && onToggleFavorite != null) {
                FavoriteButton(favorite, onToggleFavorite, Modifier.align(Alignment.TopEnd).padding(4.dp), onPhoto = true)
            }
            FirstFreeBadge(salon.nextFreeAt, now, Modifier.align(Alignment.BottomStart).padding(12.dp))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                salon.name,
                style = TerminTheme.type.title3,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            SalonMetaRow(salon, distanceKm)
        }
    }
}

@Composable
private fun CompactSalonCard(
    salon: Salon,
    photoUrl: String?,
    distanceKm: Double?,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier,
    favorite: Boolean?,
    onToggleFavorite: (() -> Unit)?,
) {
    val colors = TerminTheme.colors
    TerminCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TerminPhoto(
                url = photoUrl,
                contentDescription = stringResource(R.string.cd_photo_of, salon.name),
                placeholderLabel = salon.name,
                modifier = Modifier
                    .size(96.dp)
                    .clip(TerminTheme.radii.mediumShape)
                    .sharedSalonPhoto(salon.id),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(salon.name, style = TerminTheme.type.title3, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(salon.address, style = TerminTheme.type.callout, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                SalonMetaRow(salon, distanceKm)
                FirstFreeBadge(salon.nextFreeAt, now, Modifier)
            }
            if (favorite != null && onToggleFavorite != null) {
                FavoriteButton(favorite, onToggleFavorite, Modifier, onPhoto = false)
            }
        }
    }
}

@Composable
fun SalonMetaRow(salon: Salon, distanceKm: Double?) {
    val f = formatter
    val colors = TerminTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        RatingSummary(salon.rating.average, salon.rating.count, f.rating(salon.rating.average), f.reviews(salon.rating.count), compact = true)
        if (distanceKm != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TerminIconImage(TerminIcon.NearMe, contentDescription = null, tint = colors.textSecondary, size = 16.dp)
                Text(f.distance(distanceKm), style = TerminTheme.type.callout, color = colors.textSecondary)
            }
        }
        if (salon.minPrice != null) {
            Text(stringResource(R.string.salon_from_price, f.price(salon.minPrice)), style = TerminTheme.type.callout, color = colors.textSecondary)
        }
    }
}

@Composable
fun FirstFreeBadge(nextFreeAt: Instant?, now: Instant, modifier: Modifier) {
    val colors = TerminTheme.colors
    val f = formatter
    val text = if (nextFreeAt == null) {
        stringResource(R.string.salon_no_free_slots)
    } else {
        val today = f.date(now)
        val day = f.date(nextFreeAt)
        val dayText = f.relativeDay(day, today)
        stringResource(R.string.format_first_free, "$dayText ${f.time(nextFreeAt)}")
    }
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = if (nextFreeAt == null) colors.surfaceMuted else colors.accentSoft,
        contentColor = if (nextFreeAt == null) colors.textSecondary else colors.onAccentSoft,
        shape = TerminTheme.radii.buttonShape,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TerminIconImage(TerminIcon.Schedule, contentDescription = null, size = 16.dp, tint = androidx.compose.material3.LocalContentColor.current)
            Text(text, style = TerminTheme.type.caption)
        }
    }
}

@Composable
fun FavoriteButton(favorite: Boolean, onToggle: () -> Unit, modifier: Modifier, onPhoto: Boolean) {
    if (!TerminTheme.features.favorites) return
    val colors = TerminTheme.colors
    Box(modifier) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = if (onPhoto) colors.surface.copy(alpha = 0.9f) else Color.Transparent,
        ) {
            TerminIconButton(
                icon = if (favorite) TerminIcon.FavoriteFilled else TerminIcon.Favorite,
                contentDescription = stringResource(if (favorite) R.string.cd_favorite_remove else R.string.cd_favorite_add),
                onClick = onToggle,
                tint = if (favorite) colors.accentText else colors.text,
            )
        }
    }
}
