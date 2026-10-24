package rs.termin.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import rs.termin.app.R
import rs.termin.app.core.designsystem.NavigationStyle
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.icon.TerminIcon
import rs.termin.app.core.designsystem.icon.TerminIconImage
import rs.termin.app.core.domain.StaffSession
import rs.termin.app.core.ui.isMediumOrWider
import kotlin.reflect.KClass

/** Glavni tab: ruta, natpis i ikonice (obrisna / puna za izabrani). */
data class Tab(val route: Any, val routeClass: KClass<*>, val label: Int, val icon: TerminIcon, val selectedIcon: TerminIcon)

val ClientTabs = listOf(
    Tab(HomeRoute, HomeRoute::class, R.string.nav_home, TerminIcon.Home, TerminIcon.HomeFilled),
    Tab(SearchRoute(), SearchRoute::class, R.string.nav_search, TerminIcon.Search, TerminIcon.Search),
    Tab(AppointmentsRoute, AppointmentsRoute::class, R.string.nav_appointments, TerminIcon.Event, TerminIcon.EventFilled),
    Tab(ProfileRoute, ProfileRoute::class, R.string.nav_profile, TerminIcon.Person, TerminIcon.PersonFilled),
)

val StaffTabs = listOf(
    Tab(CalendarRoute, CalendarRoute::class, R.string.nav_calendar, TerminIcon.CalendarMonth, TerminIcon.CalendarMonthFilled),
    Tab(StaffAppointmentsRoute, StaffAppointmentsRoute::class, R.string.nav_staff_appointments, TerminIcon.Event, TerminIcon.EventFilled),
    Tab(SalonManageRoute, SalonManageRoute::class, R.string.nav_salon, TerminIcon.Storefront, TerminIcon.StorefrontFilled),
    Tab(ProfileRoute, ProfileRoute::class, R.string.nav_profile, TerminIcon.Person, TerminIcon.PersonFilled),
)

private fun NavDestination?.isOn(tab: Tab): Boolean = this?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true

/**
 * Okvir glavnog dela: donja traka na telefonu, bočna (navigation rail) na širim ekranima.
 * Traka se vidi samo na glavnim tabovima; detalji zauzimaju ceo ekran.
 */
@Composable
fun MainScaffold(staff: StaffSession?, deepLink: Uri?, onDeepLinkHandled: () -> Unit) {
    // Klijent i osoblje imaju različit početni ekran, pa i zaseban back stack.
    val navController = key(staff != null) { rememberNavController() }
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val tabs = if (staff != null) StaffTabs else ClientTabs
    val showBar = tabs.any { destination.isOn(it) }
    val wide = isMediumOrWider()
    val colors = TerminTheme.colors
    val bookFab = staff == null && TerminTheme.config.navigation == NavigationStyle.BottomBarWithBookFab
    val density = LocalDensity.current
    val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

    val onTab: (Tab) -> Unit = { tab -> navController.navigateTab(tab.route) }
    val onBook: () -> Unit = { navController.navigateTab(SearchRoute(freeToday = true), restore = false) }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            if (showBar) {
                NavigationRail(
                    containerColor = colors.surface,
                    contentColor = colors.text,
                    header = if (bookFab) {
                        { BookFab(onBook) }
                    } else {
                        null
                    },
                ) {
                    tabs.forEach { tab ->
                        val selected = destination.isOn(tab)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onTab(tab) },
                            icon = { TerminIconImage(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label), style = TerminTheme.type.caption) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = colors.onAccentSoft,
                                selectedTextColor = colors.text,
                                indicatorColor = colors.accentSoft,
                                unselectedIconColor = colors.textSecondary,
                                unselectedTextColor = colors.textSecondary,
                            ),
                        )
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                TerminNavHost(navController, staff, PaddingValues(top = statusTop, bottom = navBottom), deepLink, onDeepLinkHandled)
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    TerminNavHost(navController, staff, PaddingValues(top = statusTop), deepLink, onDeepLinkHandled)
                }
                if (showBar) {
                    NavigationBar(containerColor = colors.surface, contentColor = colors.text, tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            val selected = destination.isOn(tab)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onTab(tab) },
                                icon = { TerminIconImage(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.label), style = TerminTheme.type.caption, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.onAccentSoft,
                                    selectedTextColor = colors.text,
                                    indicatorColor = colors.accentSoft,
                                    unselectedIconColor = colors.textSecondary,
                                    unselectedTextColor = colors.textSecondary,
                                ),
                            )
                        }
                    }
                }
            }
            if (bookFab && showBar && destination.isOn(ClientTabs.first())) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = TerminTheme.spacing.gutter, bottom = navBottom + BAR_HEIGHT + 16.dp),
                ) { BookFab(onBook) }
            }
        }
    }
}

@Composable
private fun BookFab(onClick: () -> Unit) {
    val colors = TerminTheme.colors
    FloatingActionButton(onClick = onClick, containerColor = colors.accent, contentColor = colors.onAccent) {
        TerminIconImage(TerminIcon.EventAvailable, contentDescription = stringResource(R.string.nav_book), tint = colors.onAccent)
    }
}

private val BAR_HEIGHT = 80.dp
