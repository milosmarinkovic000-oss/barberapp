package rs.termin.app.feature.owner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.termin.app.R
import rs.termin.app.core.data.firebase.ImageSize
import rs.termin.app.core.designsystem.SalonAccentTheme
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.color.ColorMath
import rs.termin.app.core.designsystem.color.SalonAccentOptions
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.EmptyState
import rs.termin.app.core.designsystem.component.ErrorState
import rs.termin.app.core.designsystem.component.Illustration
import rs.termin.app.core.designsystem.component.LoadingSkeleton
import rs.termin.app.core.designsystem.component.Notice
import rs.termin.app.core.designsystem.component.NoticeTone
import rs.termin.app.core.designsystem.component.Overline
import rs.termin.app.core.designsystem.component.RatingSummary
import rs.termin.app.core.designsystem.component.SkeletonLines
import rs.termin.app.core.designsystem.component.TerminBottomSheet
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.TerminCard
import rs.termin.app.core.designsystem.component.TerminPhoto
import rs.termin.app.core.designsystem.component.TerminTextField
import rs.termin.app.core.designsystem.component.TerminTopBar
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.domain.model.Review
import rs.termin.app.core.domain.model.Salon
import rs.termin.app.core.domain.repository.AuthRepository
import rs.termin.app.core.domain.repository.SalonRepository
import rs.termin.app.core.domain.repository.StaffRepository
import rs.termin.app.core.domain.staffSession
import rs.termin.app.core.ui.Loadable
import rs.termin.app.core.ui.LocalImageUrls
import rs.termin.app.core.ui.formatter
import rs.termin.app.core.ui.messageRes
import rs.termin.app.feature.profile.FormScaffold
import rs.termin.app.feature.salon.ReviewItem
import javax.inject.Inject

// ---------------- Recenzije ----------------

data class ReplyDraft(val review: Review, val text: String, val sending: Boolean = false, val error: Throwable? = null)

data class ReviewsUiState(
    val salon: Salon? = null,
    val reviews: Loadable<List<Review>> = Loadable.Loading,
    val reply: ReplyDraft? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReviewsManageViewModel @Inject constructor(
    auth: AuthRepository,
    salons: SalonRepository,
    private val staff: StaffRepository,
) : ViewModel() {
    private val reply = MutableStateFlow<ReplyDraft?>(null)
    private val retry = MutableStateFlow(0)
    private val salonId = auth.authState.map { it.staffSession()?.salonId }.distinctUntilChanged()

    private val data = combine(salonId, retry) { id, _ -> id }.flatMapLatest { id ->
        if (id == null) {
            flowOf<Pair<Salon?, Loadable<List<Review>>>>(null to Loadable.Loading)
        } else {
            combine(salons.observeSalon(id), salons.observeReviews(id, REVIEW_LIMIT)) { salon, list ->
                salon to (Loadable.Ready(list) as Loadable<List<Review>>)
            }.onStart { emit(null to Loadable.Loading) }.catch { emit(null to Loadable.Failed(it)) }
        }
    }

    val uiState: StateFlow<ReviewsUiState> = combine(data, reply) { (salon, list), r -> ReviewsUiState(salon, list, r) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), ReviewsUiState())

    fun openReply(review: Review) = reply.update { ReplyDraft(review, review.reply.orEmpty()) }

    fun closeReply() = reply.update { null }

    fun onReplyText(text: String) = reply.update { it?.copy(text = text.take(MAX_REPLY)) }

    fun sendReply() {
        val draft = reply.value ?: return
        val salonId = uiState.value.salon?.id ?: return
        if (draft.text.isBlank()) return
        reply.update { it?.copy(sending = true, error = null) }
        viewModelScope.launch {
            try {
                staff.replyToReview(salonId, draft.review.id, draft.text.trim())
                reply.update { null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reply.update { it?.copy(sending = false, error = e) }
            }
        }
    }

    fun retry() = retry.update { it + 1 }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val REVIEW_LIMIT = 50L
        const val MAX_REPLY = 600
    }
}

@Composable
fun ReviewsManageScreen(onBack: () -> Unit, viewModel: ReviewsManageViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val f = formatter
    val spacing = TerminTheme.spacing
    Scaffold(
        containerColor = TerminTheme.colors.background,
        topBar = { TerminTopBar(stringResource(R.string.reviews_title), onBack = onBack) },
    ) { padding ->
        when (val list = state.reviews) {
            Loadable.Loading -> LoadingSkeleton(Modifier.padding(padding).padding(spacing.gutter)) { SkeletonLines(6) }
            is Loadable.Failed -> ErrorState(viewModel::retry, Modifier.padding(padding))
            is Loadable.Ready -> if (list.value.isEmpty()) {
                EmptyState(Illustration.Star, stringResource(R.string.reviews_title), stringResource(R.string.reviews_empty), Modifier.padding(padding))
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + spacing.lg),
                ) {
                    state.salon?.let { salon ->
                        item(key = "summary") {
                            RatingSummary(
                                salon.rating.average,
                                salon.rating.count,
                                f.rating(salon.rating.average),
                                f.reviews(salon.rating.count),
                                Modifier.padding(horizontal = spacing.gutter, vertical = spacing.sm),
                            )
                        }
                    }
                    items(list.value, key = { it.id }) { review ->
                        ReviewItem(review, f) {
                            TerminButton(
                                stringResource(if (review.reply == null) R.string.reviews_reply else R.string.reviews_edit_reply),
                                { viewModel.openReply(review) },
                                variant = ButtonVariant.Text,
                                icon = TerminIcon.Edit,
                            )
                        }
                    }
                }
            }
        }
    }
    state.reply?.let { draft ->
        TerminBottomSheet(viewModel::closeReply) {
            Column(Modifier.imePadding().padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(stringResource(R.string.reviews_reply), style = TerminTheme.type.title2, color = TerminTheme.colors.text, modifier = Modifier.semantics { heading() })
                Text("„${draft.review.text.ifBlank { "★".repeat(draft.review.rating) }}“ — ${draft.review.authorName}", style = TerminTheme.type.callout, color = TerminTheme.colors.textSecondary)
                draft.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }
                TerminTextField(draft.text, viewModel::onReplyText, stringResource(R.string.reviews_reply_hint), singleLine = false, minLines = 3, modifier = Modifier.fillMaxWidth())
                TerminButton(stringResource(R.string.reviews_reply_send), viewModel::sendReply, enabled = draft.text.isNotBlank(), loading = draft.sending, fullWidth = true)
            }
        }
    }
}

// ---------------- Izgled salona ----------------

data class BrandingUiState(
    val salon: Salon? = null,
    val cover: String? = null,
    val accent: Long? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: Throwable? = null,
)

@HiltViewModel
class SalonBrandingViewModel @Inject constructor(
    auth: AuthRepository,
    salons: SalonRepository,
    private val staff: StaffRepository,
) : ViewModel() {
    private val state = MutableStateFlow(BrandingUiState())
    val uiState: StateFlow<BrandingUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val salonId = auth.authState.map { it.staffSession()?.salonId }.filterNotNull().first()
            runCatching { salons.observeSalon(salonId).filterNotNull().first() }
                .onSuccess { salon ->
                    state.update {
                        it.copy(
                            salon = salon,
                            cover = salon.coverPhoto ?: salon.photos.firstOrNull(),
                            accent = salon.accentColor ?: SalonAccentOptions.first().toArgb().toLong().and(ARGB_MASK),
                        )
                    }
                }
                .onFailure { e -> state.update { it.copy(error = e) } }
        }
    }

    fun selectCover(path: String) = state.update { it.copy(cover = path, saved = false) }

    fun selectAccent(color: Color) = state.update { it.copy(accent = color.toArgb().toLong().and(ARGB_MASK), saved = false) }

    fun save() {
        val s = state.value
        val salon = s.salon ?: return
        val accent = s.accent ?: return
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                staff.setSalonBranding(salon.id, s.cover, accent)
                state.update { it.copy(saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(error = e) }
            } finally {
                state.update { it.copy(saving = false) }
            }
        }
    }

    private companion object {
        const val ARGB_MASK = 0xFFFFFFFFL
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SalonBrandingScreen(onBack: () -> Unit, viewModel: SalonBrandingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val images = LocalImageUrls.current
    val colors = TerminTheme.colors
    FormScaffold(stringResource(R.string.branding_title), onBack) {
        state.error?.let { Notice(stringResource(it.messageRes()), tone = NoticeTone.Error) }
        val salon = state.salon
        if (salon == null) {
            SkeletonLines(5)
            return@FormScaffold
        }
        Overline(stringResource(R.string.branding_cover))
        if (salon.photos.isEmpty()) {
            Notice(stringResource(R.string.branding_cover_none), icon = TerminIcon.PhotoLibrary)
        } else {
            LazyRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(salon.photos, key = { it }) { path ->
                    val selected = path == state.cover
                    Box(
                        Modifier
                            .width(140.dp)
                            .clip(TerminTheme.radii.mediumShape)
                            .border(BorderStroke(if (selected) 3.dp else 0.dp, colors.accent), TerminTheme.radii.mediumShape)
                            .selectable(selected = selected, onClick = { viewModel.selectCover(path) }, role = Role.RadioButton),
                    ) {
                        TerminPhoto(images.url(path, ImageSize.Thumb), contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                        if (selected) {
                            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).background(colors.accent, CircleShape).padding(4.dp)) {
                                TerminIconImage(TerminIcon.Check, contentDescription = null, tint = colors.onAccent, size = 18.dp)
                            }
                        }
                    }
                }
            }
        }
        Overline(stringResource(R.string.branding_accent))
        Text(stringResource(R.string.branding_accent_text), style = TerminTheme.type.callout, color = colors.textSecondary)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SalonAccentOptions.forEachIndexed { index, option ->
                val value = option.toArgb().toLong().and(0xFFFFFFFFL)
                val selected = value == state.accent
                val label = stringResource(R.string.branding_accent_option, index + 1)
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) colors.text else colors.outline), CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(option)
                        .selectable(selected = selected, onClick = { viewModel.selectAccent(option) }, role = Role.RadioButton)
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) TerminIconImage(TerminIcon.Check, contentDescription = null, tint = ColorMath.bestOn(option))
                }
            }
        }
        Overline(stringResource(R.string.branding_preview))
        val accent = state.accent?.let { Color(it.toInt()) }
        SalonAccentTheme(accent) {
            BrandingPreview(salon, state.cover)
        }
        if (state.saved) Notice(stringResource(R.string.branding_saved), tone = NoticeTone.Success)
        TerminButton(stringResource(R.string.action_save), viewModel::save, loading = state.saving, fullWidth = true)
    }
}

/** Zaglavlje stranice salona u izabranoj boji (isti elementi koje klijent vidi). */
@Composable
private fun BrandingPreview(salon: Salon, cover: String?) {
    val colors = TerminTheme.colors
    val f = formatter
    TerminCard(Modifier.fillMaxWidth()) {
        TerminPhoto(LocalImageUrls.current.url(cover), contentDescription = null, placeholderLabel = salon.name, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(salon.name, style = TerminTheme.type.title2, color = colors.text)
            RatingSummary(salon.rating.average, salon.rating.count, f.rating(salon.rating.average), f.reviews(salon.rating.count))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(TerminTheme.radii.buttonShape)
                    .background(colors.accent)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.appearance_preview_button), style = TerminTheme.type.button, color = colors.onAccent)
            }
        }
    }
}
