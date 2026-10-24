package rs.termin.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rs.termin.app.R
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.component.ButtonVariant
import rs.termin.app.core.designsystem.component.Illustration
import rs.termin.app.core.designsystem.component.IllustrationImage
import rs.termin.app.core.designsystem.component.Monogram
import rs.termin.app.core.designsystem.component.TerminButton
import rs.termin.app.core.designsystem.component.readableWidth

private data class Page(val illustration: Illustration, val title: Int, val text: Int)

private val pages = listOf(
    Page(Illustration.Scissors, R.string.onboarding_1_title, R.string.onboarding_1_text),
    Page(Illustration.Calendar, R.string.onboarding_2_title, R.string.onboarding_2_text),
    Page(Illustration.Inbox, R.string.onboarding_3_title, R.string.onboarding_3_text),
)

/** Uvod od 3 ekrana; može da se preskoči. Dugmad su u donjem delu ekrana (zona palca). */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val colors = TerminTheme.colors
    val spacing = TerminTheme.spacing
    val last = pager.currentPage == pages.lastIndex
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.gutter, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Monogram(size = 40.dp)
            Box(Modifier.weight(1f))
            if (!last) TerminButton(stringResource(R.string.onboarding_skip), onFinish, variant = ButtonVariant.Text)
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            val label = stringResource(R.string.onboarding_page, index + 1, pages.size)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.gutter)
                    .readableWidth()
                    .semantics { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
            ) {
                IllustrationImage(page.illustration, Modifier.size(220.dp))
                Text(
                    stringResource(page.title),
                    style = TerminTheme.type.display,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(page.text), style = TerminTheme.type.body, color = colors.textSecondary, textAlign = TextAlign.Center)
            }
        }
        Row(
            Modifier
                .padding(vertical = spacing.sm)
                .clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pages.indices.forEach { i ->
                Box(
                    Modifier
                        .size(if (i == pager.currentPage) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (i == pager.currentPage) colors.accentText else colors.divider),
                )
            }
        }
        TerminButton(
            text = stringResource(if (last) R.string.onboarding_start else R.string.onboarding_next),
            onClick = {
                if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            fullWidth = true,
            modifier = Modifier
                .padding(horizontal = spacing.gutter, vertical = spacing.md)
                .readableWidth(),
        )
    }
}
