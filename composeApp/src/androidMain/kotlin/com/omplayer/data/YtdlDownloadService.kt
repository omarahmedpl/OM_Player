package com.omplayer.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.omplayer.BuildConfig
import com.omplayer.player.YtdlResolvedStream
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Keeps the resolved stream transfer alive when Discover is left or the activity is recreated. */
class YtdlDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = mutableMapOf<String, Job>()
    private var latestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        when (intent?.action) {
            ACTION_CANCEL -> {
                startForegroundCompat(notification("Cancelling download", 0f))
                val trackId = intent.getStringExtra(EXTRA_TRACK_ID).orEmpty()
                jobs[trackId]?.cancel(CancellationException("Download cancelled"))
                if (jobs.isEmpty()) {
                    stopForeground(true)
                    stopSelfResult(startId)
                }
            }
            ACTION_DOWNLOAD -> startDownload(intent, startId)
            else -> stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    private fun startDownload(intent: Intent, startId: Int) {
        startForegroundCompat(notification("Starting download", 0f))
        val receiver = intent.resultReceiver()
        val trackId = intent.getStringExtra(EXTRA_TRACK_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Audio" }
        val outputBase = intent.getStringExtra(EXTRA_OUTPUT_BASE).orEmpty()
        val stream = intent.resolvedStream()
        if (!BuildConfig.DISCOVER || receiver == null || trackId.isBlank() || outputBase.isBlank() || stream == null) {
            receiver?.send(RESULT_FAILED, Bundle().apply { putString(EXTRA_MESSAGE, "The download request was incomplete.") })
            stopForeground(true)
            stopSelfResult(startId)
            return
        }
        if (jobs.containsKey(trackId)) {
            receiver.send(RESULT_FAILED, Bundle().apply { putString(EXTRA_MESSAGE, "This track is already downloading.") })
            return
        }

        jobs[trackId] = serviceScope.launch {
            try {
                val path = YtdlGoSource(applicationContext).downloadResolved(
                    outputBase = outputBase,
                    stream = stream,
                    onProgress = { progress ->
                        startForegroundCompat(notification(title, progress))
                        receiver.send(RESULT_PROGRESS, Bundle().apply { putFloat(EXTRA_PROGRESS, progress) })
                    },
                    isCancelled = { !coroutineContext.isActive },
                )
                receiver.send(RESULT_DONE, Bundle().apply { putString(EXTRA_PATH, path) })
            } catch (_: CancellationException) {
                receiver.send(RESULT_CANCELLED, Bundle())
            } catch (error: Exception) {
                receiver.send(RESULT_FAILED, Bundle().apply {
                    putString(EXTRA_MESSAGE, error.message?.take(512).orEmpty().ifBlank { "The download failed." })
                })
            } finally {
                jobs.remove(trackId)
                if (jobs.isEmpty()) {
                    stopForeground(true)
                    stopSelfResult(latestStartId)
                }
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(title: String, progress: Float): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW),
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (progress > 0f) "Downloading" else "Starting download")
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(1000, (progress.coerceIn(0f, 1f) * 1000).toInt(), progress <= 0f)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_DOWNLOAD = "com.omplayer.action.DOWNLOAD_RESOLVED_STREAM"
        private const val ACTION_CANCEL = "com.omplayer.action.CANCEL_RESOLVED_STREAM"
        private const val CHANNEL_ID = "om_downloads"
        private const val NOTIFICATION_ID = 7402
        private const val EXTRA_TRACK_ID = "track_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_OUTPUT_BASE = "output_base"
        private const val EXTRA_URL = "stream_url"
        private const val EXTRA_LENGTH = "stream_length"
        private const val EXTRA_COOKIE = "stream_cookie"
        private const val EXTRA_MIME = "stream_mime"
        private const val EXTRA_ITAG = "stream_itag"
        private const val EXTRA_RECEIVER = "receiver"
        private const val EXTRA_PROGRESS = "progress"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_MESSAGE = "message"
        private const val RESULT_PROGRESS = 1
        private const val RESULT_DONE = 2
        private const val RESULT_FAILED = 3
        private const val RESULT_CANCELLED = 4

        internal suspend fun download(
            context: Context,
            trackId: String,
            title: String,
            outputBase: String,
            stream: YtdlResolvedStream,
            isCancelled: () -> Boolean,
            onProgress: (Float) -> Unit,
        ): String = withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                val appContext = context.applicationContext
                var cancelSent = false
                fun sendCancel() {
                    if (cancelSent) return
                    cancelSent = true
                    runCatching {
                        startServiceCompat(appContext, Intent(appContext, YtdlDownloadService::class.java).apply {
                            action = ACTION_CANCEL
                            putExtra(EXTRA_TRACK_ID, trackId)
                        })
                    }
                }
                val pollCancellation = object : Runnable {
                    override fun run() {
                        if (!continuation.isActive) return
                        if (isCancelled()) sendCancel() else handler.postDelayed(this, 200)
                    }
                }
                fun finish(result: Result<String>) {
                    handler.removeCallbacks(pollCancellation)
                    if (continuation.isActive) continuation.resumeWith(result)
                }
                val receiver = object : ResultReceiver(handler) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        if (!continuation.isActive) {
                            sendCancel()
                            return
                        }
                        when (resultCode) {
                            RESULT_PROGRESS -> {
                                onProgress(resultData?.getFloat(EXTRA_PROGRESS, 0f) ?: 0f)
                                if (isCancelled()) sendCancel()
                            }
                            RESULT_DONE -> finish(Result.success(resultData?.getString(EXTRA_PATH).orEmpty()))
                            RESULT_CANCELLED -> finish(Result.failure(DownloadCancelled()))
                            RESULT_FAILED -> finish(Result.failure(IllegalStateException(resultData?.getString(EXTRA_MESSAGE).orEmpty())))
                        }
                    }
                }
                val intent = Intent(appContext, YtdlDownloadService::class.java).apply {
                    action = ACTION_DOWNLOAD
                    putExtra(EXTRA_TRACK_ID, trackId)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_OUTPUT_BASE, outputBase)
                    putExtra(EXTRA_URL, stream.url)
                    putExtra(EXTRA_LENGTH, stream.contentLength)
                    putExtra(EXTRA_COOKIE, stream.consentCookie)
                    putExtra(EXTRA_MIME, stream.mimeType)
                    putExtra(EXTRA_ITAG, stream.itag)
                    putExtra(EXTRA_RECEIVER, receiver)
                }
                try {
                    startServiceCompat(appContext, intent)
                    handler.postDelayed(pollCancellation, 200)
                } catch (error: Exception) {
                    finish(Result.failure(error))
                }
                continuation.invokeOnCancellation {
                    handler.removeCallbacks(pollCancellation)
                    handler.post { sendCancel() }
                }
            }
        }

        private fun Intent.resultReceiver(): ResultReceiver? =
            if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(EXTRA_RECEIVER, ResultReceiver::class.java)
            else @Suppress("DEPRECATION") getParcelableExtra(EXTRA_RECEIVER)

        private fun Intent.resolvedStream(): YtdlResolvedStream? {
            val url = getStringExtra(EXTRA_URL)?.takeIf { it.startsWith("https://") } ?: return null
            val length = getLongExtra(EXTRA_LENGTH, 0L).takeIf { it > 0L } ?: return null
            val cookie = getStringExtra(EXTRA_COOKIE)?.takeIf { it.startsWith("CONSENT=") } ?: return null
            val mime = getStringExtra(EXTRA_MIME)?.takeIf(String::isNotBlank) ?: return null
            val itag = getIntExtra(EXTRA_ITAG, 0).takeIf { it > 0 } ?: return null
            return YtdlResolvedStream(url, length, cookie, mime, itag)
        }

        private fun startServiceCompat(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }
    }
}
