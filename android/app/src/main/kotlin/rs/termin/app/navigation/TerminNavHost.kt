package rs.termin.app.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import rs.termin.app.BuildConfig
import rs.termin.app.core.data.notifications.DeepLinks
import rs.termin.app.core.designsystem.TerminTheme
import rs.termin.app.core.designsystem.tokens.Motion
import rs.termin.app.core.domain.StaffSession
import rs.termin.app.feature.appointments.AppointmentDetailScreen
import rs.termin.app.feature.appointments.AppointmentsScreen
import rs.termin.app.feature.booking.BookingScreen
import rs.termin.app.feature.booking.BookingSuccessScreen
import rs.termin.app.feature.calendar.CalendarScreen
import rs.termin.app.feature.debug.StylePreviewScreen
import rs.termin.app.feature.home.HomeScreen
import rs.termin.app.feature.inbox.InboxScreen
import rs.termin.app.feature.inbox.InboxTarget
import rs.termin.app.feature.owner.IntegrationsScreen
import rs.termin.app.feature.owner.ManageActions
import rs.termin.app.feature.owner.ReviewsManageScreen
import rs.termin.app.feature.owner.SalonBrandingScreen
import rs.termin.app.feature.owner.SalonInfoScreen
import rs.termin.app.feature.owner.SalonManageScreen
import rs.termin.app.feature.owner.ServiceEditScreen
import rs.termin.app.feature.owner.ServicesScreen
import rs.termin.app.feature.owner.StatsScreen
import rs.termin.app.feature.owner.StylistEditScreen
import rs.termin.app.feature.owner.TeamScreen
import rs.termin.app.feature.owner.TimeOffScreen
import rs.termin.app.feature.profile.AppearanceScreen
import rs.termin.app.feature.profile.CreateSalonScreen
import rs.termin.app.feature.profile.DeleteAccountScreen
import rs.termin.app.feature.profile.EditProfileScreen
import rs.termin.app.feature.profile.FavoritesScreen
import rs.termin.app.feature.profile.LicensesScreen
import rs.termin.app.feature.profile.PrivacyScreen
import rs.termin.app.feature.profile.ProfileActions
import rs.termin.app.feature.profile.ProfileScreen
import rs.termin.app.feature.salon.SalonScreen
import rs.termin.app.feature.search.SearchScreen
import rs.termin.app.feature.staff.StaffAppointmentDetailScreen
import rs.termin.app.feature.staff.StaffAppointmentsScreen

private const val SCHEME = DeepLinks.SCHEME

private fun forward(motion: Motion): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (motion.enabled) fadeIn(motion.tweenSpec()) + slideInHorizontally(motion.offsetSpec()) { it / 8 } else EnterTransition.None
}

private fun backward(motion: Motion): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (motion.enabled) fadeOut(motion.tweenSpec()) + slideOutHorizontally(motion.offsetSpec()) { it / 8 } else ExitTransition.None
}

private fun fade(motion: Motion): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (motion.enabled) fadeOut(motion.tweenSpec()) else ExitTransition.None
}

private fun fadeEnter(motion: Motion): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (motion.enabled) fadeIn(motion.tweenSpec()) else EnterTransition.None
}

/**
 * Sve destinacije aplikacije. Graf je isti za klijenta i osoblje; razlikuju se početni ekran
 * i to gde vodi link na termin (detalj za klijenta ili za salon).
 */
@Composable
fun TerminNavHost(
    navController: NavHostController,
    staff: StaffSession?,
    tabPadding: PaddingValues,
    deepLink: Uri?,
    onDeepLinkHandled: () -> Unit,
) {
    val motion = TerminTheme.motion
    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            // Nepoznat link (npr. stara verzija) se tiho preskače.
            runCatching { navController.navigate(deepLink) }
            onDeepLinkHandled()
        }
    }
    NavHost(
        navController = navController,
        startDestination = if (staff != null) CalendarRoute else HomeRoute,
        enterTransition = forward(motion),
        exitTransition = fade(motion),
        popEnterTransition = fadeEnter(motion),
        popExitTransition = backward(motion),
    ) {
        clientTabs(navController, tabPadding)
        staffTabs(navController, tabPadding)
        details(navController, staff != null)
        account(navController)
        management(navController)
    }
}

/** Prelazak na glavni tab (čuva stanje tabova). */
fun NavHostController.navigateTab(route: Any, restore: Boolean = true) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = restore
    }
}

private fun NavGraphBuilder.clientTabs(nav: NavHostController, padding: PaddingValues) {
    composable<HomeRoute> {
        HomeScreen(
            contentPadding = padding,
            onOpenSalon = { nav.navigate(SalonRoute(it)) },
            onSearch = { category, freeToday -> nav.navigateTab(SearchRoute(category, freeToday), restore = false) },
        )
    }
    composable<SearchRoute> {
        SearchScreen(
            contentPadding = padding,
            onOpenSalon = { nav.navigate(SalonRoute(it)) },
            detailPane = { salonId ->
                SalonScreen(salonId, onBack = null, onBook = { services, stylist -> nav.navigate(BookingRoute(salonId, services, stylist)) })
            },
        )
    }
    composable<AppointmentsRoute> {
        AppointmentsScreen(
            contentPadding = padding,
            onOpenAppointment = { nav.navigate(AppointmentRoute(it)) },
            onFindSalon = { nav.navigateTab(SearchRoute(), restore = false) },
            onBookFromWaitlist = { salonId, date -> nav.navigate(BookingRoute(salonId, date = date.toString())) },
            detailPane = { id -> ClientAppointmentDetail(nav, id, onBack = null) },
        )
    }
    composable<ProfileRoute> {
        ProfileScreen(
            contentPadding = padding,
            actions = ProfileActions(
                onEditProfile = { nav.navigate(EditProfileRoute) },
                onFavorites = { nav.navigate(FavoritesRoute) },
                onInbox = { nav.navigate(InboxRoute) },
                onAppearance = { nav.navigate(AppearanceRoute) },
                onPrivacy = { nav.navigate(PrivacyRoute) },
                onLicenses = { nav.navigate(LicensesRoute) },
                onCreateSalon = { nav.navigate(CreateSalonRoute) },
                onDeleteAccount = { nav.navigate(DeleteAccountRoute) },
                onStylePreview = { nav.navigate(StylePreviewRoute) },
            ),
        )
    }
}

private fun NavGraphBuilder.staffTabs(nav: NavHostController, padding: PaddingValues) {
    composable<CalendarRoute> {
        CalendarScreen(
            contentPadding = padding,
            onOpenAppointment = { nav.navigate(StaffAppointmentRoute(it)) },
            onOpenTeam = { nav.navigate(StylistsRoute) },
        )
    }
    composable<StaffAppointmentsRoute> {
        StaffAppointmentsScreen(
            contentPadding = padding,
            onOpen = { nav.navigate(StaffAppointmentRoute(it)) },
            detailPane = { id -> StaffAppointmentDetailScreen(id, onBack = null) },
        )
    }
    composable<SalonManageRoute> {
        SalonManageScreen(
            contentPadding = padding,
            actions = ManageActions(
                onInfo = { nav.navigate(SalonInfoRoute) },
                onServices = { nav.navigate(ServicesRoute) },
                onTeam = { nav.navigate(StylistsRoute) },
                onStats = { nav.navigate(StatsRoute) },
                onReviews = { nav.navigate(ReviewsManageRoute) },
                onAppearance = { nav.navigate(SalonAppearanceRoute) },
                onIntegrations = { nav.navigate(IntegrationsRoute()) },
                onPreview = { nav.navigate(SalonRoute(it)) },
                onMyProfile = { nav.navigate(StylistEditRoute(it)) },
                onTimeOff = { nav.navigate(TimeOffRoute(it)) },
            ),
        )
    }
}

@Composable
private fun ClientAppointmentDetail(nav: NavHostController, id: String, onBack: (() -> Unit)?) {
    AppointmentDetailScreen(
        appointmentId = id,
        onBack = onBack,
        onReschedule = { salonId, services, stylist, rescheduleId -> nav.navigate(BookingRoute(salonId, services, stylist, rescheduleId = rescheduleId)) },
        onBookAgain = { salonId, services, stylist -> nav.navigate(BookingRoute(salonId, services, stylist)) },
        onOpenSalon = { nav.navigate(SalonRoute(it)) },
    )
}

private fun NavGraphBuilder.details(nav: NavHostController, staffMode: Boolean) {
    composable<SalonRoute>(
        deepLinks = listOf(
            navDeepLink<SalonRoute>(basePath = "$SCHEME://salon"),
            navDeepLink<SalonRoute>(basePath = "https://${BuildConfig.APP_LINK_HOST}/s"),
        ),
    ) { entry ->
        val salonId = entry.toRoute<SalonRoute>().salonId
        SalonScreen(salonId, onBack = { nav.popBackStack() }, onBook = { services, stylist -> nav.navigate(BookingRoute(salonId, services, stylist)) })
    }
    composable<BookingRoute>(deepLinks = listOf(navDeepLink<BookingRoute>(basePath = "$SCHEME://book"))) {
        BookingScreen(
            onClose = { nav.popBackStack() },
            onCompleted = { id ->
                nav.navigate(BookingSuccessRoute(id)) { popUpTo<BookingRoute> { inclusive = true } }
            },
        )
    }
    composable<BookingSuccessRoute> {
        BookingSuccessScreen(onDone = { nav.navigateTab(AppointmentsRoute, restore = false) })
    }
    // Link „termin://appointment/{id}“ vodi u detalj koji odgovara trenutnom prikazu.
    val appointmentLink = listOf(navDeepLink<AppointmentRoute>(basePath = "$SCHEME://appointment"))
    composable<AppointmentRoute>(deepLinks = if (staffMode) emptyList() else appointmentLink) { entry ->
        ClientAppointmentDetail(nav, entry.toRoute<AppointmentRoute>().appointmentId, onBack = { nav.popBackStack() })
    }
    composable<StaffAppointmentRoute>(
        deepLinks = if (staffMode) listOf(navDeepLink<StaffAppointmentRoute>(basePath = "$SCHEME://appointment")) else emptyList(),
    ) { entry ->
        StaffAppointmentDetailScreen(entry.toRoute<StaffAppointmentRoute>().appointmentId, onBack = { nav.popBackStack() })
    }
    composable<InboxRoute>(deepLinks = listOf(navDeepLink<InboxRoute>(basePath = "$SCHEME://inbox"))) {
        InboxScreen(
            onBack = { nav.popBackStack() },
            onOpen = { target ->
                when (target) {
                    is InboxTarget.Appointment ->
                        nav.navigate(if (staffMode && target.staff) StaffAppointmentRoute(target.appointmentId) else AppointmentRoute(target.appointmentId))
                    is InboxTarget.Book ->
                        nav.navigate(BookingRoute(target.salonId, target.services.joinToString(",").ifEmpty { null }, target.stylistId, target.date))
                    is InboxTarget.Salon -> nav.navigate(SalonRoute(target.salonId))
                    InboxTarget.Nowhere -> Unit
                }
            },
        )
    }
}

private fun NavGraphBuilder.account(nav: NavHostController) {
    composable<FavoritesRoute> {
        FavoritesScreen(
            onBack = { nav.popBackStack() },
            onOpenSalon = { nav.navigate(SalonRoute(it)) },
            onBookStylist = { salonId, stylistId -> nav.navigate(BookingRoute(salonId, stylist = stylistId)) },
        )
    }
    composable<AppearanceRoute> { AppearanceScreen(onBack = { nav.popBackStack() }) }
    composable<PrivacyRoute> { PrivacyScreen(onBack = { nav.popBackStack() }) }
    composable<LicensesRoute> { LicensesScreen(onBack = { nav.popBackStack() }) }
    composable<DeleteAccountRoute> { DeleteAccountScreen(onBack = { nav.popBackStack() }) }
    composable<EditProfileRoute> { EditProfileScreen(onBack = { nav.popBackStack() }) }
    // Posle otvaranja salona uloga vlasnika stiže preko tokena i aplikacija prelazi u prikaz za salon.
    composable<CreateSalonRoute> { CreateSalonScreen(onBack = { nav.popBackStack() }, onCreated = { nav.popBackStack() }) }
    if (BuildConfig.DEBUG) {
        composable<StylePreviewRoute> { StylePreviewScreen(onBack = { nav.popBackStack() }) }
    }
}

private fun NavGraphBuilder.management(nav: NavHostController) {
    composable<SalonInfoRoute> { SalonInfoScreen(onBack = { nav.popBackStack() }) }
    composable<ServicesRoute> { ServicesScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(ServiceEditRoute(it)) }) }
    composable<ServiceEditRoute> { ServiceEditScreen(onBack = { nav.popBackStack() }) }
    composable<StylistsRoute> { TeamScreen(onBack = { nav.popBackStack() }, onOpenStylist = { nav.navigate(StylistEditRoute(it)) }) }
    composable<StylistEditRoute> { entry ->
        StylistEditScreen(
            stylistId = entry.toRoute<StylistEditRoute>().stylistId,
            onBack = { nav.popBackStack() },
            onTimeOff = { nav.navigate(TimeOffRoute(it)) },
            onIntegrations = { nav.navigate(IntegrationsRoute()) },
        )
    }
    composable<TimeOffRoute> { entry -> TimeOffScreen(entry.toRoute<TimeOffRoute>().stylistId, onBack = { nav.popBackStack() }) }
    composable<IntegrationsRoute>(deepLinks = listOf(navDeepLink<IntegrationsRoute>(basePath = "$SCHEME://integrations"))) {
        IntegrationsScreen(onBack = { nav.popBackStack() })
    }
    composable<StatsRoute> { StatsScreen(onBack = { nav.popBackStack() }) }
    composable<ReviewsManageRoute> { ReviewsManageScreen(onBack = { nav.popBackStack() }) }
    composable<SalonAppearanceRoute> { SalonBrandingScreen(onBack = { nav.popBackStack() }) }
}
