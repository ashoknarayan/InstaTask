package com.instatask.feature.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mikepenz.markdown.m3.Markdown
import com.instatask.core.database.ReelEntity
import com.instatask.core.model.ReelType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    onOpenTalkWithMe: () -> Unit,
    onOpenVault: () -> Unit,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val reels by viewModel.reels.collectAsState()
    val stopwatchReflection by viewModel.stopwatchReflection.collectAsState()
    if (reels.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val pagerState = rememberPagerState(pageCount = { reels.size })
    var commentReel by remember { mutableStateOf<ReelEntity?>(null) }

    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            ReelCard(
                reel = reels[page],
                onLike = { viewModel.toggleLike(reels[page]) },
                onComment = { commentReel = reels[page] },
                onAction = {
                    when (reels[page].reelType) {
                        ReelType.TASK -> viewModel.startTask(reels[page])
                        ReelType.PERMISSION -> viewModel.approvePermission(reels[page])
                        ReelType.PLAN, ReelType.MOTIVATIONAL, ReelType.INFO ->
                            viewModel.toggleStar(reels[page])
                    }
                },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onOpenVault) { Text("VAULT") }
            FloatingActionButton(onClick = onOpenTalkWithMe) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Talk With Me")
            }
            TextButton(onClick = onOpenTalkWithMe) { Text("TALK") }
        }
    }

    val reflectionReel = stopwatchReflection?.reel
    val activeCommentReel = reflectionReel ?: commentReel
    activeCommentReel?.let { reel ->
        CommentSheet(
            reel = reel,
            sessionDurationMs = stopwatchReflection
                ?.takeIf { it.reel.reelId == reel.reelId }
                ?.elapsedMs,
            onDismiss = {
                if (reflectionReel?.reelId == reel.reelId) {
                    viewModel.dismissStopwatchReflection()
                } else {
                    commentReel = null
                }
            },
            onSubmit = { text ->
                val session = stopwatchReflection?.takeIf { it.reel.reelId == reel.reelId }
                viewModel.addComment(reel, text, session?.elapsedMs)
                if (session != null) viewModel.dismissStopwatchReflection() else commentReel = null
            },
        )
    }
}

@Composable
private fun ReelCard(
    reel: ReelEntity,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onAction: () -> Unit,
) {
    val gradient = if (reel.backgroundThemeId == "permission_black") {
        Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF000000)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF202C4A), Color(0xFF10131A)))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(gradient)
            .statusBarsPadding()
            .padding(20.dp),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(bottom = 64.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xCC111827),
        ) {
            val markdown = reel.markdownContent
            if (isMarkdownSafeForRenderer(markdown)) {
                Markdown(content = markdown, modifier = Modifier.padding(24.dp))
            } else {
                PlainReelText(markdown)
            }
        }

        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            RailButton(
                if (reel.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                "Like",
                onLike,
            )
            RailButton(Icons.AutoMirrored.Filled.Comment, "Comment", onComment)
            RailButton(
                when (reel.reelType) {
                    ReelType.TASK -> Icons.Default.Timer
                    ReelType.PERMISSION -> Icons.Default.Check
                    ReelType.PLAN, ReelType.MOTIVATIONAL, ReelType.INFO -> Icons.Default.Star
                },
                when (reel.reelType) {
                    ReelType.TASK -> "Start focus"
                    ReelType.PERMISSION -> "Approve lookup"
                    else -> if (reel.isStarred) "Unstar" else "Star"
                },
                onAction,
            )
        }
    }
}

private fun isMarkdownSafeForRenderer(markdown: String): Boolean {
    if (markdown.length > 10_000 || '\u0000' in markdown) return false
    val codeFenceCount = markdown.lineSequence()
        .count { it.trimStart().startsWith("```") }
    return codeFenceCount % 2 == 0
}

@Composable
private fun PlainReelText(markdown: String) {
    Text(
        text = markdown.take(10_000),
        modifier = Modifier.padding(24.dp),
        color = Color.White,
        fontSize = 22.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun RailButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = onClick, shape = CircleShape) { Icon(icon, contentDescription = label) }
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentSheet(
    reel: ReelEntity,
    sessionDurationMs: Long?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp).navigationBarsPadding()) {
            Text(
                if (sessionDurationMs == null) "Reflection" else "Focus reflection",
                style = MaterialTheme.typography.titleLarge,
            )
            if (sessionDurationMs != null) {
                Text(
                    "Focused on ${reel.markdownContent.lineSequence().firstOrNull().orEmpty()} " +
                        "for ${sessionDurationMs / 60_000} min",
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Finished completely, got 80% done, or got distracted…") },
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onSubmit(text) }, modifier = Modifier.fillMaxWidth()) {
                Text("Send reflection")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
