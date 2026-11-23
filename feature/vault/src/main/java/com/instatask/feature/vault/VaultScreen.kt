package com.instatask.feature.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.instatask.core.database.InstaTaskDao
import com.instatask.core.database.ReelEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(private val dao: InstaTaskDao) : ViewModel() {
    val starred = dao.observeStarredReels()
    val liked = dao.observeLikedReels()

    fun unstar(reel: ReelEntity) =
        viewModelScope.launch { dao.setReelStarred(reel.reelId, false, null) }

    fun unlike(reel: ReelEntity) =
        viewModelScope.launch { dao.setReelLiked(reel.reelId, false) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    var tab by remember { mutableIntStateOf(0) }
    val starred by viewModel.starred.collectAsState(initial = emptyList())
    val liked by viewModel.liked.collectAsState(initial = emptyList())
    val reels = if (tab == 0) starred else liked

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Starred") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Liked") })
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(reels, key = { it.reelId }) { reel ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 2.dp,
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(reel.markdownContent)
                            TextButton(
                                onClick = {
                                    if (tab == 0) viewModel.unstar(reel) else viewModel.unlike(reel)
                                },
                            ) {
                                Text(if (tab == 0) "Remove from starred" else "Unlike")
                            }
                        }
                    }
                }
            }
        }
    }
}
