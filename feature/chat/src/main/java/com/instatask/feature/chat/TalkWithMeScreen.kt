package com.instatask.feature.chat

import android.app.Application
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.instatask.core.database.ChatMessageEntity
import com.instatask.core.database.InstaTaskDao
import com.instatask.core.worker.FeedOrdering
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TalkViewModel @Inject constructor(
    app: Application,
    private val dao: InstaTaskDao,
) : AndroidViewModel(app) {
    val messages = dao.observeChatMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun send(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            dao.insertChatMessage(
                ChatMessageEntity(
                    messageId = UUID.randomUUID().toString(),
                    isFromUser = true,
                    text = text,
                    createdAtEpochMs = System.currentTimeMillis(),
                ),
            )
            FeedOrdering.enqueueBatch(WorkManager.getInstance(getApplication()))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalkWithMeScreen(
    onBack: () -> Unit,
    viewModel: TalkViewModel = hiltViewModel(),
) {
    var text by remember { mutableStateOf("") }
    var showPrompts by remember { mutableStateOf(true) }
    val messages by viewModel.messages.collectAsState()
    val prompts = listOf(
        "What are your upcoming tasks or deadlines?",
        "What are your long-term goals?",
        "What motivates you to achieve them?",
        "What are your peak energy times?",
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Talk With Me") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Onboarding prompts")
                Row {
                    Text("Skip prompts")
                    Switch(checked = !showPrompts, onCheckedChange = { showPrompts = !it })
                }
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages, key = { it.messageId }) { message ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (message.isFromUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(message.text, modifier = Modifier.padding(12.dp))
                    }
                }
                if (showPrompts) {
                    items(prompts) { prompt ->
                        SuggestionChip(
                            onClick = { text = prompt },
                            label = { Text(prompt) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add tasks, goals, constraints, or reflections…") },
                minLines = 3,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.send(text)
                    text = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Send to background planner") }
        }
    }
}
