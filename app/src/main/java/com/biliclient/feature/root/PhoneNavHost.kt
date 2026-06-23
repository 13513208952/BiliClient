package com.biliclient.feature.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import com.biliclient.feature.tv.common.tvEdgeAware
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.biliclient.feature.bangumi.BangumiScreen
import com.biliclient.feature.downloads.DownloadsScreen
import com.biliclient.feature.fav.FavContentScreen
import com.biliclient.feature.fav.FavScreen
import com.biliclient.feature.history.HistoryScreen
import com.biliclient.feature.home.HomeScreen
import com.biliclient.feature.login.LoginScreen
import com.biliclient.feature.mine.MineScreen
import com.biliclient.feature.search.SearchScreen
import com.biliclient.feature.settings.SettingsScreen
import com.biliclient.feature.video.VideoScreen

private val TOP_LEVEL_ROUTES = setOf("home", "mine")

@Composable
fun PhoneNavHost() {
    val nav = rememberNavController()
    val backEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentRoute in TOP_LEVEL_ROUTES) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == "home",
                        onClick = {
                            nav.navigate("home") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("首页") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == "mine",
                        onClick = {
                            nav.navigate("mine") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Person, null) },
                        label = { Text("我的") }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .tvEdgeAware(
                    onLeftEdge  = if (currentRoute == "mine") {
                        { nav.navigate("home") { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }
                    } else null,
                    onRightEdge = if (currentRoute == "home") {
                        { nav.navigate("mine") { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }
                    } else null
                )
        ) {
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.fillMaxSize()
        ) {
            // ---- 顶级 tab ----
            composable("home") {
                HomeScreen(
                    onSearch = { nav.navigate("search") },
                    onOpenVideo = { nav.navigate("video/$it") }
                )
            }
            composable("mine") {
                MineScreen(
                    onLogin = { nav.navigate("login") },
                    onFav = { nav.navigate("fav") },
                    onHistory = { nav.navigate("history") },
                    onDownloads = { nav.navigate("downloads") },
                    onSettings = { nav.navigate("settings") }
                )
            }

            // ---- 详情页 ----
            composable("search") {
                SearchScreen(
                    onBack = { nav.popBackStack() },
                    onOpenVideo = { nav.navigate("video/$it") },
                    onOpenBangumi = { nav.navigate("bangumi/$it") }
                )
            }
            composable("login") {
                LoginScreen(onLoggedIn = { nav.popBackStack() })
            }
            composable(
                "video/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                VideoScreen(
                    id            = entry.arguments?.getString("id").orEmpty(),
                    onBack        = { nav.popBackStack() },
                    onOpenVideo   = { nav.navigate("video/$it") },
                    onOpenBangumi = { nav.navigate("bangumi/$it") }
                )
            }
            composable(
                "bangumi/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                BangumiScreen(
                    id     = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() }
                )
            }
            composable("downloads") { DownloadsScreen() }
            composable("settings") { SettingsScreen() }
            composable("fav") {
                FavScreen(
                    onBack = { nav.popBackStack() },
                    onOpenFavContent = { nav.navigate("fav/$it") }
                )
            }
            composable(
                "fav/{mediaId}",
                arguments = listOf(navArgument("mediaId") { type = NavType.StringType })
            ) {
                FavContentScreen(
                    onBack = { nav.popBackStack() },
                    onOpenVideo = { nav.navigate("video/$it") }
                )
            }
            composable("history") {
                HistoryScreen(
                    onBack = { nav.popBackStack() },
                    onOpenVideo = { nav.navigate("video/$it") }
                )
            }
        }
        } // end Box (tvEdgeAware)
    }
}
