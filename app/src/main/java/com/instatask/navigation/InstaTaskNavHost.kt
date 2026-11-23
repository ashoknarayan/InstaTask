package com.instatask.navigation

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.instatask.feature.chat.TalkWithMeScreen
import com.instatask.feature.feed.FeedScreen
import com.instatask.feature.vault.VaultScreen

@Composable
fun InstaTaskNavHost() {
    val navController = rememberNavController()
    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "feed") {
            composable("feed") {
                FeedScreen(
                    onOpenTalkWithMe = { navController.navigate("chat") },
                    onOpenVault = { navController.navigate("vault") },
                )
            }
            composable("chat") {
                TalkWithMeScreen(onBack = { navController.popBackStack() })
            }
            composable("vault") {
                VaultScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
