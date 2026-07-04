package com.example.campusbuddy.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.toRoute
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.campusbuddy.data.repository.CampusBuddyRepository
import com.example.campusbuddy.ui.auth.SplashScreen
import com.example.campusbuddy.ui.auth.LoginScreen
import com.example.campusbuddy.ui.auth.SignupScreen
import com.example.campusbuddy.ui.auth.ForgotPasswordScreen
import com.example.campusbuddy.ui.setup.*
import com.example.campusbuddy.ui.home.HomeScreen
import com.example.campusbuddy.ui.requests.*
import com.example.campusbuddy.ui.chats.*
import com.example.campusbuddy.ui.profile.*
import com.example.campusbuddy.ui.partner.PartnerProfileScreen
import com.example.campusbuddy.ui.matches.MatchesScreen
import com.example.campusbuddy.ui.notifications.NotificationsScreen
import com.example.campusbuddy.ui.report.ReportUserScreen

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any
)

val bottomNavItems = listOf(
    BottomNavItem("Home", Icons.Filled.Home, Icons.Outlined.Home, HomeRoute),
    BottomNavItem("Requests", Icons.Filled.List, Icons.Outlined.ListAlt, RequestsRoute),
    BottomNavItem("Chats", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubble, ChatsRoute),
    BottomNavItem("Profile", Icons.Filled.Person, Icons.Outlined.Person, ProfileRoute)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusBuddyNavHost(
    repository: CampusBuddyRepository
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hasRoute(item.route::class) == true
    }

    val authScreens = listOf(
        SplashRoute::class, LoginRoute::class, SignupRoute::class,
        ForgotPasswordRoute::class,
        ProfileSetupRoute::class, OnboardingRoute::class
    )

    val showTopBar = !authScreens.any { route ->
        currentDestination?.hasRoute(route) == true
    } && currentDestination?.hasRoute(SettingsRoute::class) == false

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hasRoute(item.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SplashRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Auth Layer
            composable<SplashRoute> {
                SplashScreen(
                    repository = repository,
                    onNavigateToLogin = {
                        navController.navigate(LoginRoute) {
                            popUpTo(SplashRoute) { inclusive = true }
                        }
                    },
                    onNavigateToHome = {
                        navController.navigate(HomeRoute) {
                            popUpTo(SplashRoute) { inclusive = true }
                        }
                    },
                    onNavigateToProfileSetup = {
                        navController.navigate(ProfileSetupRoute) {
                            popUpTo(SplashRoute) { inclusive = true }
                        }
                    }
                )
            }

            composable<LoginRoute> {
                LoginScreen(
                    repository = repository,
                    onNavigateToSignup = { navController.navigate(SignupRoute) },
                    onNavigateToForgotPassword = { navController.navigate(ForgotPasswordRoute) },
                    onNavigateToHome = {
                        navController.navigate(HomeRoute) {
                            popUpTo(SplashRoute) { inclusive = true }
                        }
                    }
                )
            }

            composable<SignupRoute> {
                SignupScreen(
                    repository = repository,
                    onNavigateToProfileSetup = { navController.navigate(ProfileSetupRoute) { popUpTo(SplashRoute) { inclusive = true } } },
                    onNavigateToLogin = { navController.popBackStack() }
                )
            }

            composable<ForgotPasswordRoute> {
                ForgotPasswordScreen(
                    repository = repository,
                    onBack = { navController.popBackStack() }
                )
            }

            // Setup Layer
            composable<ProfileSetupRoute> {
                ProfileSetupScreen(
                    repository = repository,
                    onNavigateToOnboarding = {
                        navController.navigate(OnboardingRoute) {
                            popUpTo(SplashRoute) { inclusive = true }
                        }
                    },
                    onNavigateToHome = {
                        navController.navigate(HomeRoute) {
                            popUpTo(SplashRoute) { inclusive = true }
                        }
                    }
                )
            }

            composable<OnboardingRoute> {
                OnboardingScreen(
                    onNavigateToHome = {
                        navController.navigate(HomeRoute) {
                            popUpTo(SplashRoute) { inclusive = true }
                        }
                    }
                )
            }

            // Main Layer (tabs)
            composable<HomeRoute> {
                HomeScreen(
                    repository = repository,
                    onNavigateToCreateRequest = { navController.navigate(CreateRequestRoute) },
                    onNavigateToRequestDetails = { requestId ->
                        navController.navigate(RequestDetailsRoute(requestId))
                    },
                    onNavigateToPartnerProfile = { userId ->
                        navController.navigate(PartnerProfileRoute(userId))
                    },
                    onNavigateToNotifications = { navController.navigate(NotificationsRoute) },
                    onNavigateToMatches = { navController.navigate(MatchesRoute) }
                )
            }

            composable<RequestsRoute> {
                RequestsScreen(
                    repository = repository,
                    onNavigateToCreateRequest = { navController.navigate(CreateRequestRoute) },
                    onNavigateToRequestDetails = { requestId ->
                        navController.navigate(RequestDetailsRoute(requestId))
                    },
                    onNavigateToMyRequestDetails = { requestId ->
                        navController.navigate(MyRequestDetailsRoute(requestId))
                    }
                )
            }

            composable<ChatsRoute> {
                ChatsScreen(
                    repository = repository,
                    onNavigateToChat = { conversationId ->
                        navController.navigate(ChatRoute(conversationId))
                    }
                )
            }

            composable<ProfileRoute> {
                ProfileScreen(
                    repository = repository,
                    onNavigateToEditProfile = { navController.navigate(EditProfileRoute) },
                    onNavigateToSettings = { navController.navigate(SettingsRoute) },
                    onNavigateToLogin = {
                        navController.navigate(LoginRoute) {
                            popUpTo(SplashRoute) { inclusive = true }
                        }
                    },
                    onNavigateToMatches = { navController.navigate(MatchesRoute) },
                    onNavigateToNotifications = { navController.navigate(NotificationsRoute) }
                )
            }

            // Detail Layer
            composable<CreateRequestRoute> {
                CreateRequestScreen(
                    repository = repository,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<RequestDetailsRoute> { backStackEntry ->
                val args = backStackEntry.toRoute<RequestDetailsRoute>()
                RequestDetailsScreen(
                    requestId = args.requestId,
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onNavigateToPartnerProfile = { userId ->
                        navController.navigate(PartnerProfileRoute(userId))
                    }
                )
            }

            composable<MyRequestDetailsRoute> { backStackEntry ->
                val args = backStackEntry.toRoute<MyRequestDetailsRoute>()
                MyRequestDetailsScreen(
                    requestId = args.requestId,
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onNavigateToPartnerProfile = { userId ->
                        navController.navigate(PartnerProfileRoute(userId))
                    },
                    onNavigateToChat = { conversationId ->
                        navController.navigate(ChatRoute(conversationId))
                    }
                )
            }

            composable<PartnerProfileRoute> { backStackEntry ->
                val args = backStackEntry.toRoute<PartnerProfileRoute>()
                PartnerProfileScreen(
                    partnerUserId = args.partnerUserId,
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onNavigateToChat = { conversationId ->
                        navController.navigate(ChatRoute(conversationId))
                    },
                    onNavigateToReport = { userId, userName ->
                        navController.navigate(ReportUserRoute(userId, userName))
                    }
                )
            }

            composable<ChatRoute> { backStackEntry ->
                val args = backStackEntry.toRoute<ChatRoute>()
                ChatScreen(
                    conversationId = args.conversationId,
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onNavigateToPartnerProfile = { userId ->
                        navController.navigate(PartnerProfileRoute(userId))
                    }
                )
            }

            composable<MatchesRoute> {
                MatchesScreen(
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onNavigateToChat = { conversationId ->
                        navController.navigate(ChatRoute(conversationId))
                    },
                    onNavigateToPartnerProfile = { userId ->
                        navController.navigate(PartnerProfileRoute(userId))
                    }
                )
            }

            composable<NotificationsRoute> {
                NotificationsScreen(
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onNavigateToMyRequestDetails = { requestId ->
                        navController.navigate(MyRequestDetailsRoute(requestId))
                    },
                    onNavigateToChat = { conversationId ->
                        navController.navigate(ChatRoute(conversationId))
                    },
                    onNavigateToMyRequests = {
                        navController.navigate(RequestsRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToProfile = {
                        navController.navigate(ProfileRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable<EditProfileRoute> {
                EditProfileScreen(
                    repository = repository,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<SettingsRoute> {
                SettingsScreen(
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onNavigateToLogin = {
                        navController.navigate(LoginRoute) {
                            popUpTo(SplashRoute) { inclusive = true }
                        }
                    }
                )
            }

            composable<ReportUserRoute> { backStackEntry ->
                val args = backStackEntry.toRoute<ReportUserRoute>()
                ReportUserScreen(
                    targetUserId = args.targetUserId,
                    targetUserName = args.targetUserName,
                    repository = repository,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
