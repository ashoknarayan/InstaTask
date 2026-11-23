package com.instatask.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.instatask.core.ai.LocalRagRetriever
import com.instatask.core.database.InstaTaskDao
import com.instatask.core.model.MemorySource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@HiltWorker
class ConstrainedWebSearchWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val dao: InstaTaskDao,
    private val ragRetriever: LocalRagRetriever,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val query = inputData.getString("query")?.take(180) ?: return Result.failure()
        val reelId = inputData.getString("reelId")
        val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=$encoded")
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile) InstaTask/1.0")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return Result.retry()
        val snippets = Jsoup.parse(response.body?.string().orEmpty())
            .select(".result__snippet")
            .take(4)
            .joinToString(" ") { it.text() }
            .take(1500)
        if (snippets.isBlank()) return Result.retry()
        ragRetriever.indexMemory(MemorySource.WEB_SNIPPET, snippets)
        reelId?.let {
            dao.approvePermissionReel(it, "## Research Approved\n\nThis topic is queued for background synthesis.")
        }
        return Result.success()
    }
}
