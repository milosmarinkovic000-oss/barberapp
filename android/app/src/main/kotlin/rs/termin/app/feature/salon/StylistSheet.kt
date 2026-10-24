package rs.termin.app.feature.salon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import rs.termin.app.R
import rs.termin.app.core.data.firebase.ImageSize
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.Avatar
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.ChipRow
import rs.termin.app.core.designsystem.component.RatingSummary
import rs.termin.app.core.designsystem.component.TerminBottomSheet
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminChip
import rs.termin.app.core.designsystem.component.TerminPhoto
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.ui.LocalImageUrls
import rs.termin.app.core.ui.formatter

/** Profil frizera: portfolio, specijalnosti, ocena i prvi slobodan termin. */
@Composable
fun StylistSheet(
    stylist: Stylist,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBook: () -> Unit,
    onDismiss: () -> Unit,
) {
    val images = LocalImageUrls.current
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val f = formatter
    TerminBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                Modifier.padding(horizontal = spacing.gutter),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(images.url(stylist.photoPath, ImageSize.Thumb), stylist.name, size = 80.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stylist.name, style = TerminTheme.type.title2, color = colors.text, modifier = Modifier.semantics { heading() })
                    RatingSummary(stylist.rating.average, stylist.rating.count, f.rating(stylist.rating.average), f.reviews(stylist.rating.count))
                }
            }
            if (stylist.bio.isNotBlank()) {
                Text(stylist.bio, style = TerminTheme.type.body, color = colors.text, modifier = Modifier.padding(horizontal = spacing.gutter))
            }
            if (stylist.specialties.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.stylist_specialties), style = TerminTheme.type.title3, modifier = Modifier.padding(horizontal = spacing.gutter))
                    ChipRow { stylist.specialties.forEach { TerminChip(it, selected = false, onClick = {}, enabled = false) } }
                }
            }
            if (stylist.portfolio.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.stylist_portfolio), style = TerminTheme.type.title3, modifier = Modifier.padding(horizontal = spacing.gutter))
                    LazyRow(contentPadding = PaddingValues(horizontal = spacing.gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(stylist.portfolio, key = { _, path -> path }) { index, path ->
                            TerminPhoto(
                                url = images.url(path, ImageSize.Thumb),
                                contentDescription = stringResource(R.string.stylist_portfolio_item, stylist.name, index + 1, stylist.portfolio.size),
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(TerminTheme.radii.mediumShape),
                            )
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.stylist_first_free), style = TerminTheme.type.title3, color = colors.text)
                val next = stylist.nextFreeAt
                Text(
                    if (next == null) stringResource(R.string.stylist_no_free) else f.dateTime(next),
                    style = TerminTheme.type.body,
                    color = colors.textSecondary,
                )
            }
            Column(Modifier.padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminButton(stringResource(R.string.salon_book_with, stylist.name), onBook, fullWidth = true, icon = TerminIcon.EventAvailable)
                if (TerminTheme.features.favorites) {
                    TerminButton(
                        stringResource(if (favorite) R.string.cd_favorite_remove else R.string.cd_favorite_add),
                        onToggleFavorite,
                        variant = ButtonVariant.Text,
                        icon = if (favorite) TerminIcon.FavoriteFilled else TerminIcon.Favorite,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
