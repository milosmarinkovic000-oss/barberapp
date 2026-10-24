package rs.termin.app.feature.salon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import rs.termin.app.BuildConfig
import rs.termin.app.R
import rs.termin.app.core.data.firebase.ImageSize
import rs.termin.app.core.designsystem.SalonAccentTheme
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.Avatar
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.EmptyState
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.Illustration
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.RatingSummary
import rs.termin.app.core.designsystem.component.SkeletonBox
import rs.termin.app.core.designsystem.component.SkeletonLines
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminDivider
import rs.termin.app.core.designsystem.component.TerminIconButton
import rs.termin.app.core.designsystem.component.TerminPhoto
import rs.termin.app.core.designsystem.component.raised
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.domain.model.BelgradeZone
import rs.termin.app.core.domain.model.Review
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.model.Service
import rs.termin.app.core.domain.model.Stylist
import rs.termin.app.core.ui.Actions
import rs.termin.app.core.ui.Formatter
import rs.termin.app.core.ui.LocalImageUrls
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.formatter
import rs.termin.app.core.ui.sharedSalonPhoto
import java.time.DayOfWeek
import java.time.LocalDate
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Stranica salona. Ceo ekran koristi akcentnu boju salona (ako je vlasnik izabrao), a
 * zaglavlje sa galerijom se skuplja uz paralaksu.
 */
@Composable
fun SalonScreen(
    salonId: String,
    onBack: (() -> Unit)?,
    onBook: (services: String?, stylist: String?) -> Unit,
) {
    val viewModel = hiltViewModel<SalonViewModel, SalonViewModel.Factory>(key = salonId) { it.create(salonId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accent = state.content.valueOrNull()?.salon?.accentColor?.let { ComposeColor(it) }
    SalonAccentTheme(accent) {
        SalonContentView(
            state = state,
            onBack = onBack,
            onBook = onBook,
            onTab = viewModel::selectTab,
            onToggleService = viewModel::toggleService,
            onOpenStylist = viewModel::openStylist,
            onToggleFavorite = viewModel::toggleFavorite,
            onToggleFavoriteStylist = viewModel::toggleFavoriteStylist,
            onRetry = viewModel::retry,
        )
    }
}

@Composable
fun SalonContentView(
    state: SalonUiState,
    onBack: (() -> Unit)?,
    onBook: (String?, String?) -> Unit,
    onTab: (SalonTab) -> Unit,
    onToggleService: (String) -> Unit,
    onOpenStylist: (String?) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleFavoriteStylist: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = TerminTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        when (val content = state.content) {
            Loadable.Loading -> SalonSkeleton()
            is Loadable.Failed -> Column(Modifier.statusBarsPadding()) { ErrorState(onRetry) }
            is Loadable.Ready -> {
                val data = content.value
                if (data == null) {
                    Column(Modifier.statusBarsPadding()) {
                        EmptyState(Illustration.Search, stringResource(R.string.state_error_title), stringResource(R.string.salon_not_found))
                    }
                } else {
                    SalonLoaded(data, state, onBook, onTab, onToggleService, onOpenStylist, onToggleFavorite)
                    state.openStylistId?.let { id ->
                        data.stylists.firstOrNull { it.id == id }?.let { stylist ->
                            StylistSheet(
                                stylist = stylist,
                                favorite = "${data.salon.id}/${stylist.id}" in state.favoriteStylists,
                                onToggleFavorite = { onToggleFavoriteStylist(stylist.id) },
                                onBook = {
                                    onOpenStylist(null)
                                    onBook(state.selectedServiceIds.toRouteArg(), stylist.id)
                                },
                                onDismiss = { onOpenStylist(null) },
                            )
                        }
                    }
                }
            }
        }
        if (onBack != null) {
            TerminIconButton(
                TerminIcon.ArrowBack,
                stringResource(R.string.cd_back),
                onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .background(colors.surface.copy(alpha = 0.9f), CircleShape),
            )
        }
    }
}

@Composable
private fun SalonLoaded(
    data: SalonContent,
    state: SalonUiState,
    onBook: (String?, String?) -> Unit,
    onTab: (SalonTab) -> Unit,
    onToggleService: (String) -> Unit,
    onOpenStylist: (String?) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val listState = rememberLazyListState()
    val motion = TerminTheme.motion
    val f = formatter
    val tabs = buildList {
        add(SalonTab.Services)
        add(SalonTab.Stylists)
        if (TerminTheme.features.reviews) add(SalonTab.Reviews)
        add(SalonTab.Info)
    }
    // Paralaksa: zaglavlje se pomera sporije od sadržaja dok se skroluje.
    val parallax by remember {
        derivedStateOf { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset * PARALLAX else 0f }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "gallery", contentType = "gallery") {
                Gallery(
                    salon = data.salon,
                    modifier = Modifier.graphicsLayer { translationY = if (motion.richEffects) parallax else 0f },
                )
            }
            item(key = "title", contentType = "title") {
                TitleBlock(data.salon, state.favorite, onToggleFavorite)
            }
            stickyHeader(key = "tabs", contentType = "tabs") {
                SalonTabs(tabs, state.tab, onTab)
            }
            when (state.tab) {
                SalonTab.Services -> servicesTab(data, state, onToggleService, f)
                SalonTab.Stylists -> stylistsTab(data, state, onOpenStylist, f)
                SalonTab.Reviews -> reviewsTab(data.reviews, f)
                SalonTab.Info -> item(key = "info", contentType = "info") { InfoTab(data.salon, f) }
            }
        }
        BookingBar(
            state = state,
            onBook = { onBook(state.selectedServiceIds.toRouteArg(), null) },
            modifier = Modifier.align(Alignment.BottomCenter),
            f = f,
        )
    }
}

@Composable
private fun Gallery(salon: Salon, modifier: Modifier) {
    val images = LocalImageUrls.current
    val photos = remember(salon) { listOfNotNull(salon.coverPhoto) + salon.photos }
    val pager = rememberPagerState { photos.size.coerceAtLeast(1) }
    Box(modifier.fillMaxWidth().aspectRatio(GALLERY_ASPECT)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val description = stringResource(R.string.salon_gallery, page + 1, photos.size.coerceAtLeast(1))
            TerminPhoto(
                url = images.url(photos.getOrNull(page)),
                contentDescription = description,
                placeholderLabel = salon.name,
                scrim = true,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (page == 0) Modifier.sharedSalonPhoto(salon.id) else Modifier),
            )
        }
        if (photos.size > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                photos.indices.forEach { i ->
                    Box(
                        Modifier
                            .padding(1.dp)
                            .background(if (i == pager.currentPage) ComposeColor.White else ComposeColor.White.copy(alpha = 0.5f), CircleShape)
                            .padding(4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleBlock(salon: Salon, favorite: Boolean, onToggleFavorite: () -> Unit) {
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val f = formatter
    val context = LocalContext.current
    val today = LocalDate.now(BelgradeZone)
    val hoursToday = salon.workingHours.on(today.dayOfWeek)
    val shareText = stringResource(R.string.salon_share_text, salon.name, salon.address, Actions.salonLink(salon.id))
    val shareTitle = stringResource(R.string.salon_share_title)
    Column(Modifier.padding(horizontal = spacing.gutter, vertical = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Overline(listOf(salon.city, stringResource(priceLevelRes(salon.priceLevel))).filter { it.isNotBlank() }.joinToString(" · "))
        Text(salon.name, style = TerminTheme.type.display, color = colors.text, modifier = Modifier.semantics { heading() })
        RatingSummary(salon.rating.average, salon.rating.count, f.rating(salon.rating.average), f.reviews(salon.rating.count))
        Text(
            if (hoursToday.isEmpty()) stringResource(R.string.salon_closed_today)
            else stringResource(R.string.salon_open_today, hoursToday.joinToString(", ") { f.time(it.start) + "–" + f.time(it.end) }),
            style = TerminTheme.type.callout,
            color = if (hoursToday.isEmpty()) colors.error else colors.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.padding(top = spacing.xs)) {
            TerminButton(stringResource(R.string.action_call), { Actions.call(context, salon.phone) }, variant = ButtonVariant.Secondary, icon = TerminIcon.Call, modifier = Modifier.weight(1f))
            TerminButton(stringResource(R.string.action_directions), { Actions.directions(context, salon.location, "${salon.address}, ${salon.city}") }, variant = ButtonVariant.Secondary, icon = TerminIcon.Directions, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            TerminButton(stringResource(R.string.action_share), { Actions.share(context, shareText, shareTitle) }, variant = ButtonVariant.Text, icon = TerminIcon.Share)
            if (TerminTheme.features.favorites) {
                TerminButton(
                    stringResource(if (favorite) R.string.cd_favorite_remove else R.string.cd_favorite_add),
                    onToggleFavorite,
                    variant = ButtonVariant.Text,
                    icon = if (favorite) TerminIcon.FavoriteFilled else TerminIcon.Favorite,
                )
            }
        }
    }
}

private fun priceLevelRes(level: Int): Int = when (level) {
    1 -> R.string.price_level_1
    3 -> R.string.price_level_3
    else -> R.string.price_level_2
}

@Composable
private fun SalonTabs(tabs: List<SalonTab>, selected: SalonTab, onTab: (SalonTab) -> Unit) {
    val colors = TerminTheme.colors
    val index = tabs.indexOf(selected).coerceAtLeast(0)
    PrimaryScrollableTabRow(
        selectedTabIndex = index,
        containerColor = colors.background,
        contentColor = colors.text,
        edgePadding = TerminTheme.spacing.gutter,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier.tabIndicatorOffset(index, matchContentSize = true),
                color = colors.accentText,
            )
        },
        divider = { TerminDivider() },
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onTab(tab) },
                selectedContentColor = colors.text,
                unselectedContentColor = colors.textSecondary,
                text = {
                    Text(
                        stringResource(
                            when (tab) {
                                SalonTab.Services -> R.string.salon_tab_services
                                SalonTab.Stylists -> R.string.salon_tab_stylists
                                SalonTab.Reviews -> R.string.salon_tab_reviews
                                SalonTab.Info -> R.string.salon_tab_info
                            },
                        ),
                        style = TerminTheme.type.bodyStrong,
                    )
                },
            )
        }
    }
}

private fun LazyListScope.servicesTab(data: SalonContent, state: SalonUiState, onToggle: (String) -> Unit, f: Formatter) {
    if (data.services.isEmpty()) {
        item(key = "no-services") { NoServices() }
        return
    }
    data.services.groupBy { it.category }.forEach { (category, services) ->
        item(key = "cat-${category.key}", contentType = "category") {
            Text(
                f.category(category),
                style = TerminTheme.type.title3,
                color = TerminTheme.colors.text,
                modifier = Modifier
                    .padding(start = TerminTheme.spacing.gutter, end = TerminTheme.spacing.gutter, top = 20.dp, bottom = 4.dp)
                    .semantics { heading() },
            )
        }
        items(services, key = { it.id }, contentType = { "service" }) { service ->
            ServiceRow(service, service.id in state.selectedServiceIds, { onToggle(service.id) }, f)
        }
    }
}

@Composable
private fun NoServices() = Notice(stringResource(R.string.salon_no_services), Modifier.padding(16.dp))

@Composable
fun ServiceRow(service: Service, selected: Boolean, onToggle: () -> Unit, f: Formatter) {
    val colors = TerminTheme.colors
    val selectedText = stringResource(if (selected) R.string.cd_selected else R.string.cd_not_selected)
    Surface(
        onClick = onToggle,
        color = if (selected) colors.accentSoft else ComposeColor.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Checkbox
                stateDescription = selectedText
            },
    ) {
        Row(
            Modifier.padding(horizontal = TerminTheme.spacing.gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(service.name, style = TerminTheme.type.bodyStrong, color = colors.text)
                if (service.description.isNotBlank()) {
                    Text(service.description, style = TerminTheme.type.callout, color = colors.textSecondary)
                }
                Text("${f.duration(service.durationMin)} · ${f.price(service.price)}", style = TerminTheme.type.callout, color = colors.text)
            }
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = colors.accentText, uncheckedColor = colors.outline, checkmarkColor = colors.surface),
            )
        }
    }
}

private fun LazyListScope.stylistsTab(data: SalonContent, state: SalonUiState, onOpen: (String?) -> Unit, f: Formatter) {
    if (data.stylists.isEmpty()) {
        item(key = "no-stylists") { NoStylists() }
        return
    }
    items(data.stylists, key = { it.id }, contentType = { "stylist" }) { stylist ->
        StylistRow(stylist, state, onOpen, f)
    }
}

@Composable
private fun NoStylists() = Notice(stringResource(R.string.salon_no_stylists), Modifier.padding(16.dp))

@Composable
private fun StylistRow(stylist: Stylist, state: SalonUiState, onOpen: (String?) -> Unit, f: Formatter) {
    val images = LocalImageUrls.current
    val colors = TerminTheme.colors
    Surface(onClick = { onOpen(stylist.id) }, color = ComposeColor.Transparent) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = TerminTheme.spacing.gutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(images.url(stylist.photoPath, ImageSize.Thumb), stylist.name, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stylist.name, style = TerminTheme.type.bodyStrong, color = colors.text)
                if (stylist.specialties.isNotEmpty()) {
                    Text(stylist.specialties.joinToString(" · "), style = TerminTheme.type.callout, color = colors.textSecondary)
                }
                RatingSummary(stylist.rating.average, stylist.rating.count, f.rating(stylist.rating.average), f.reviews(stylist.rating.count), compact = true)
                Text(firstFreeText(stylist, state, f), style = TerminTheme.type.caption, color = colors.textSecondary)
            }
            TerminIconImage(TerminIcon.ChevronRight, contentDescription = null, tint = colors.textSecondary)
        }
    }
}

@Composable
private fun firstFreeText(stylist: Stylist, state: SalonUiState, f: Formatter): String {
    val next = stylist.nextFreeAt ?: return stringResource(R.string.stylist_no_free)
    val today = f.date(state.now)
    return stringResource(R.string.format_first_free, "${f.relativeDay(f.date(next), today)} ${f.time(next)}")
}

private fun LazyListScope.reviewsTab(reviews: List<Review>, f: Formatter) {
    if (reviews.isEmpty()) {
        item(key = "no-reviews") {
            EmptyState(Illustration.Star, stringResource(R.string.salon_no_reviews_title), stringResource(R.string.salon_no_reviews))
        }
        return
    }
    items(reviews, key = { it.id }, contentType = { "review" }) { review -> ReviewItem(review, f) }
}

@Composable
fun ReviewItem(review: Review, f: Formatter, trailing: (@Composable () -> Unit)? = null) {
    val colors = TerminTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TerminTheme.spacing.gutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(review.authorName.ifBlank { "—" }, style = TerminTheme.type.bodyStrong, color = colors.text, modifier = Modifier.weight(1f))
            Text(f.dayMonth(f.date(review.createdAt)), style = TerminTheme.type.caption, color = colors.textSecondary)
        }
        StarsRow(review.rating)
        if (review.stylistName.isNotBlank()) Text(review.stylistName, style = TerminTheme.type.caption, color = colors.textSecondary)
        if (review.text.isNotBlank()) Text(review.text, style = TerminTheme.type.body, color = colors.text)
        review.reply?.let { reply ->
            Surface(color = colors.surfaceMuted, shape = TerminTheme.radii.mediumShape) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.salon_reply), style = TerminTheme.type.caption, color = colors.textSecondary)
                    Text(reply, style = TerminTheme.type.callout, color = colors.text)
                }
            }
        }
        trailing?.invoke()
        TerminDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
fun StarsRow(rating: Int) {
    val colors = TerminTheme.colors
    val label = stringResource(R.string.cd_star_input, rating)
    Row(Modifier.semantics { contentDescription = label }) {
        for (i in 1..5) {
            TerminIconImage(if (i <= rating) TerminIcon.StarFilled else TerminIcon.Star, contentDescription = null, tint = if (i <= rating) colors.accentText else colors.outline, size = 16.dp)
        }
    }
}

@Composable
private fun InfoTab(salon: Salon, f: Formatter) {
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val context = LocalContext.current
    val today = LocalDate.now(BelgradeZone).dayOfWeek
    Column(Modifier.padding(spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        if (salon.description.isNotBlank()) {
            InfoSection(stringResource(R.string.salon_about)) { Text(salon.description, style = TerminTheme.type.body, color = colors.text) }
        }
        InfoSection(stringResource(R.string.salon_hours)) {
            DayOfWeek.entries.forEach { day ->
                val ranges = salon.workingHours.on(day)
                val weekday = f.dayLong(LocalDate.now(BelgradeZone).with(java.time.temporal.TemporalAdjusters.nextOrSame(day))).substringBefore(',')
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        weekday.replaceFirstChar { it.uppercase() },
                        style = if (day == today) TerminTheme.type.bodyStrong else TerminTheme.type.body,
                        color = colors.text,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (ranges.isEmpty()) stringResource(R.string.salon_closed) else ranges.joinToString(", ") { "${f.time(it.start)}–${f.time(it.end)}" },
                        style = if (day == today) TerminTheme.type.bodyStrong else TerminTheme.type.body,
                        color = if (ranges.isEmpty()) colors.textSecondary else colors.text,
                    )
                }
            }
        }
        InfoSection(stringResource(R.string.salon_address)) {
            Text("${salon.address}, ${salon.city}", style = TerminTheme.type.body, color = colors.text)
            SalonMap(salon)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                TerminButton(stringResource(R.string.action_directions), { Actions.directions(context, salon.location, "${salon.address}, ${salon.city}") }, variant = ButtonVariant.Secondary, icon = TerminIcon.Directions)
                TerminButton(stringResource(R.string.action_open_maps), { Actions.openInMaps(context, salon.location, salon.name) }, variant = ButtonVariant.Text, icon = TerminIcon.OpenInNew)
            }
        }
        InfoSection(stringResource(R.string.salon_phone)) {
            TerminButton(salon.phone, { Actions.call(context, salon.phone) }, variant = ButtonVariant.Text, icon = TerminIcon.Call)
        }
        InfoSection(stringResource(R.string.salon_policy)) {
            PolicyText(salon, f)
        }
    }
}

@Composable
fun PolicyText(salon: Salon, f: Formatter) {
    val colors = TerminTheme.colors
    val rules = salon.rules
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (rules.cancelHours > 0) stringResource(R.string.salon_policy_cancel, rules.cancelHours) else stringResource(R.string.salon_policy_cancel_any),
            style = TerminTheme.type.body,
            color = colors.text,
        )
        if (rules.minNoticeMinutes > 0) {
            Text(stringResource(R.string.salon_policy_notice, f.duration(rules.minNoticeMinutes)), style = TerminTheme.type.callout, color = colors.textSecondary)
        }
        Text(stringResource(R.string.salon_policy_horizon, rules.horizonDays), style = TerminTheme.type.callout, color = colors.textSecondary)
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = TerminTheme.type.title3, color = TerminTheme.colors.text, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun SalonMap(salon: Salon) {
    val location = salon.location ?: return
    if (!TerminTheme.features.map) return
    if (!BuildConfig.HAS_MAPS_KEY) {
        Notice(stringResource(R.string.salon_map_unavailable), icon = TerminIcon.Map)
        return
    }
    val position = LatLng(location.latitude, location.longitude)
    val camera = rememberCameraPositionState { this.position = CameraPosition.fromLatLngZoom(position, MAP_ZOOM) }
    TerminCard(elevated = false) {
        GoogleMap(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            cameraPositionState = camera,
            properties = MapProperties(),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, scrollGesturesEnabled = false, mapToolbarEnabled = false),
        ) {
            Marker(state = MarkerState(position), title = salon.name)
        }
    }
}

@Composable
private fun BookingBar(state: SalonUiState, onBook: () -> Unit, modifier: Modifier, f: Formatter) {
    val colors = TerminTheme.colors
    val selected = state.selectedServices
    Surface(
        color = colors.surface,
        modifier = modifier
            .fillMaxWidth()
            .raised(TerminTheme.surfaces.raisedElevation, TerminTheme.radii.sheetShape),
        shape = TerminTheme.radii.sheetShape,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = TerminTheme.spacing.gutter, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selected.isNotEmpty()) {
                Text(
                    stringResource(R.string.salon_services_selected, selected.size, f.duration(state.totals.durationMin), f.price(state.totals.price)),
                    style = TerminTheme.type.callout,
                    color = colors.textSecondary,
                )
            }
            TerminButton(
                text = if (selected.isEmpty()) stringResource(R.string.salon_book) else stringResource(R.string.salon_book_selected, selected.size),
                onClick = onBook,
                fullWidth = true,
                icon = TerminIcon.EventAvailable,
            )
        }
    }
}

@Composable
private fun SalonSkeleton() {
    Column(Modifier.fillMaxSize()) {
        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(GALLERY_ASPECT), shape = androidx.compose.ui.graphics.RectangleShape)
        Column(Modifier.padding(TerminTheme.spacing.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBox(Modifier.fillMaxWidth(0.7f).height(32.dp))
            SkeletonLines(4)
        }
    }
}

private const val PARALLAX = 0.5f
private const val GALLERY_ASPECT = 4f / 3f
private const val MAP_ZOOM = 15f
