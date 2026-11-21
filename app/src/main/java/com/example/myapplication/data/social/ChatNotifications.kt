package com.example.myapplication.data.social

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.i18n.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal const val CHAT_NOTIF_MAX_SEEN_IDS = 200

internal data class PeerNotificationPlan(
    val peerId: String,
    val senderName: String,
    val senderAvatarV: Int,
    val messages: List<ChatMessage>,
    val totalUnreadCount: Int
)

internal data class ChatNotificationDecision(
    val toNotify: List<PeerNotificationPlan>,
    val peersToCancel: Set<String>,
    val updatedSeenIds: List<String>,
    val updatedNotifiedPeers: Set<String>,
    val seededOnly: Boolean
)

internal data class FormattedChatNotification(
    val title: String,
    val text: String,
    val inboxLines: List<String>
)

internal fun notificationIdForPeer(peerId: String): Int =
    41_000 + ((peerId.hashCode() and 0x7FFF_FFFF) % 100_000)

internal fun formatChatItemLine(msg: ChatMessage): String =
    if (msg.artist.isNotBlank()) "${msg.title} — ${msg.artist}" else msg.title

internal fun formatChatNotification(plan: PeerNotificationPlan): FormattedChatNotification {
    val sender = plan.senderName.ifBlank { tr("Друг") }
    val latest = plan.messages.first()
    val count = maxOf(plan.totalUnreadCount, plan.messages.size)
    if (count <= 1) {
        val title = if (latest.isAlbum) {
            tr("%s отправил альбом", sender)
        } else {
            tr("%s отправил трек", sender)
        }
        return FormattedChatNotification(
            title = title,
            text = formatChatItemLine(latest),
            inboxLines = emptyList()
        )
    }
    return FormattedChatNotification(
        title = tr("%s · новых: %d", sender, count),
        text = formatChatItemLine(latest),
        inboxLines = plan.messages.take(5).map(::formatChatItemLine)
    )
}

internal fun planChatNotifications(
    myUserId: String,
    initializedUser: String?,
    seenIds: Collection<String>,
    notifiedPeers: Set<String>,
    inbox: ChatInbox,
    activePeerId: String?,
    appInForeground: Boolean,
    maxSeenIds: Int = CHAT_NOTIF_MAX_SEEN_IDS
): ChatNotificationDecision {
    val unreadIncoming = inbox.newItems.filter {
        it.readAt == null &&
            it.senderId != myUserId &&
            it.senderId.isNotBlank() &&
            it.id.isNotBlank()
    }
    val threadUnreadByPeer = inbox.threads.associate { it.peerId to it.unreadCount }
    val threadNameByPeer = inbox.threads.associate { it.peerId to it.shownName }

    if (initializedUser != myUserId) {
        val updatedSeen = unreadIncoming.map { it.id }.distinct().takeLast(maxSeenIds)
        return ChatNotificationDecision(
            toNotify = emptyList(),
            peersToCancel = notifiedPeers.toSet(),
            updatedSeenIds = updatedSeen,
            updatedNotifiedPeers = emptySet(),
            seededOnly = true
        )
    }

    val seen = LinkedHashSet(seenIds)
    val grouped = unreadIncoming
        .groupBy { it.senderId }
        .mapValues { (_, list) ->
            list.sortedByDescending { it.createdAtMs ?: Long.MIN_VALUE }
        }
    val suppressedPeer = if (appInForeground) activePeerId else null

    val toNotify = mutableListOf<PeerNotificationPlan>()
    val nextNotifiedPeers = LinkedHashSet(notifiedPeers)
    val peersToCancel = LinkedHashSet<String>()

    for (peerId in notifiedPeers) {
        if (peerId == suppressedPeer || (peerId !in grouped && (threadUnreadByPeer[peerId] ?: 0) == 0)) {
            peersToCancel += peerId
            nextNotifiedPeers -= peerId
        }
    }

    for ((peerId, msgs) in grouped) {
        val hasUnseen = msgs.any { it.id !in seen }
        for (msg in msgs) {
            seen += msg.id
        }
        if (peerId == suppressedPeer) {
            if (peerId in nextNotifiedPeers) {
                peersToCancel += peerId
                nextNotifiedPeers -= peerId
            }
            continue
        }
        if (hasUnseen) {
            val latest = msgs.first()
            val senderName = latest.senderShownName.ifBlank { threadNameByPeer[peerId].orEmpty() }
            val totalUnread = maxOf(msgs.size, threadUnreadByPeer[peerId] ?: 0)
            toNotify += PeerNotificationPlan(
                peerId = peerId,
                senderName = senderName,
                senderAvatarV = latest.senderAvatarV,
                messages = msgs,
                totalUnreadCount = totalUnread
            )
            nextNotifiedPeers += peerId
        }
    }

    val cappedSeen = seen.toList().let { if (it.size > maxSeenIds) it.takeLast(maxSeenIds) else it }
    return ChatNotificationDecision(
        toNotify = toNotify,
        peersToCancel = peersToCancel,
        updatedSeenIds = cappedSeen,
        updatedNotifiedPeers = nextNotifiedPeers,
        seededOnly = false
    )
}

/**
 * System notifications when friends send tracks or albums in Music Chat.
 * Fires immediately on Realtime inbox updates while the app or playback service is alive,
 * and checks periodically via [ChatNotificationWorker] when the process is stopped.
 */
object ChatNotifications {
    const val CHANNEL_ID = "chat_messages"
    private const val TAG = "ChatNotifications"
    private const val PREFS = "chat_notifications"
    private const val KEY_INIT_USER = "initialized_user"
    private const val KEY_SEEN_IDS = "seen_ids"
    private const val KEY_NOTIFIED_PEERS = "notified_peers"
    private const val KEY_PERMISSION_ASKED = "permission_asked"
    private const val WORK_NAME = "youcloud_chat_poll"
    private const val ART_TIMEOUT_MS = 2_500L
    private const val ART_TARGET_PX = 192

    private val stateLock = Any()

    @Volatile
    private var appInForeground = false

    @Volatile
    private var activeChatPeerId: String? = null

    private val artHttp by lazy {
        OkHttpClient.Builder()
            .connectTimeout(ART_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(ART_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(ART_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    fun onAppForeground() {
        appInForeground = true
    }

    fun onAppBackground() {
        appInForeground = false
    }

    fun setActivePeer(peerId: String) {
        activeChatPeerId = peerId
    }

    fun clearActivePeer(peerId: String) {
        if (activeChatPeerId == peerId) activeChatPeerId = null
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun canPost(context: Context): Boolean =
        hasPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun wasPermissionAsked(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERMISSION_ASKED, false)

    fun markPermissionAsked(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PERMISSION_ASKED, true)
            .apply()
    }

    fun ensureChannel(context: Context) {
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            tr("Чат с друзьями"),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = tr("Треки и альбомы от друзей")
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun onInboxLoadedAsync(
        scope: CoroutineScope,
        context: Context,
        myUserId: String,
        inbox: ChatInbox,
        avatarUrlOf: (peerId: String, avatarV: Int) -> String?
    ): Job? {
        val appContext = context.applicationContext
        val decision = synchronized(stateLock) {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val initializedUser = prefs.getString(KEY_INIT_USER, null)
            val seenIds = prefs.getString(KEY_SEEN_IDS, "")
                .orEmpty()
                .split('\n')
                .filter { it.isNotBlank() }
            val notifiedPeers = prefs.getStringSet(KEY_NOTIFIED_PEERS, emptySet())
                ?.let(::HashSet)
                ?: HashSet()

            val result = planChatNotifications(
                myUserId = myUserId,
                initializedUser = initializedUser,
                seenIds = seenIds,
                notifiedPeers = notifiedPeers,
                inbox = inbox,
                activePeerId = activeChatPeerId,
                appInForeground = appInForeground
            )

            prefs.edit()
                .putString(KEY_INIT_USER, myUserId)
                .putString(KEY_SEEN_IDS, result.updatedSeenIds.joinToString("\n"))
                .putStringSet(KEY_NOTIFIED_PEERS, HashSet(result.updatedNotifiedPeers))
                .apply()

            result
        }

        val notifManager = NotificationManagerCompat.from(appContext)
        for (peerId in decision.peersToCancel) {
            runCatching { notifManager.cancel(notificationIdForPeer(peerId)) }
        }

        if (decision.toNotify.isEmpty() || !canPost(appContext)) return null

        return scope.launch(Dispatchers.IO) {
            ensureChannel(appContext)
            for (plan in decision.toNotify) {
                if (appInForeground && activeChatPeerId == plan.peerId) continue
                val formatted = formatChatNotification(plan)
                val latest = plan.messages.first()
                val coverUrl = latest.coverUrl?.takeIf { it.startsWith("http") }
                val avatarUrl = avatarUrlOf(plan.peerId, plan.senderAvatarV)?.takeIf { it.startsWith("http") }
                val largeIcon = withTimeoutOrNull(ART_TIMEOUT_MS) {
                    coverUrl?.let(::fetchArtBitmap) ?: avatarUrl?.let(::fetchArtBitmap)
                }
                if (appInForeground && activeChatPeerId == plan.peerId) continue
                postPeerNotification(appContext, plan, formatted, largeIcon)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun postPeerNotification(
        appContext: Context,
        plan: PeerNotificationPlan,
        formatted: FormattedChatNotification,
        largeIcon: Bitmap?
    ) {
        if (!canPost(appContext)) return
        val notifId = notificationIdForPeer(plan.peerId)
        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_CHAT
            putExtra(MainActivity.EXTRA_CHAT_PEER_ID, plan.peerId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_chat)
            .setContentTitle(formatted.title)
            .setContentText(formatted.text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(plan.messages.first().createdAtMs ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setNumber(maxOf(plan.totalUnreadCount, plan.messages.size))

        if (largeIcon != null) {
            builder.setLargeIcon(largeIcon)
        }

        if (formatted.inboxLines.size > 1) {
            val style = NotificationCompat.InboxStyle().setBigContentTitle(formatted.title)
            formatted.inboxLines.forEach(style::addLine)
            builder.setStyle(style)
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(formatted.text))
        }

        runCatching {
            if (canPost(appContext)) {
                NotificationManagerCompat.from(appContext).notify(notifId, builder.build())
            }
        }.onFailure {
            Log.w(TAG, "Failed to post chat notification for ${plan.peerId}", it)
        }
    }

    private fun fetchArtBitmap(url: String): Bitmap? = runCatching {
        val request = Request.Builder().url(url).build()
        val bytes = artHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            response.body?.bytes()
        } ?: return@runCatching null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val rawMin = minOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        if (rawMin > ART_TARGET_PX) {
            while (rawMin / (sample * 2) >= ART_TARGET_PX) {
                sample *= 2
            }
        }
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return@runCatching null

        val side = minOf(decoded.width, decoded.height)
        if (side <= 0) return@runCatching null
        val cropped = if (decoded.width != side || decoded.height != side) {
            Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        } else {
            decoded
        }
        val scaled = if (side > ART_TARGET_PX) {
            Bitmap.createScaledBitmap(cropped, ART_TARGET_PX, ART_TARGET_PX, true)
        } else {
            cropped
        }
        if (cropped !== decoded && cropped !== scaled) cropped.recycle()
        if (decoded !== scaled) decoded.recycle()
        scaled
    }.getOrNull()

    fun cancelForPeer(context: Context, peerId: String) {
        val appContext = context.applicationContext
        synchronized(stateLock) {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val current = prefs.getStringSet(KEY_NOTIFIED_PEERS, null)
            if (current != null && peerId in current) {
                val updated = HashSet(current).apply { remove(peerId) }
                prefs.edit().putStringSet(KEY_NOTIFIED_PEERS, updated).apply()
            }
        }
        runCatching { NotificationManagerCompat.from(appContext).cancel(notificationIdForPeer(peerId)) }
    }

    fun onSignedOut(context: Context) {
        val appContext = context.applicationContext
        val peersToCancel = synchronized(stateLock) {
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val current = prefs.getStringSet(KEY_NOTIFIED_PEERS, emptySet())
                ?.let(::HashSet)
                ?: emptySet()
            prefs.edit()
                .remove(KEY_INIT_USER)
                .remove(KEY_SEEN_IDS)
                .remove(KEY_NOTIFIED_PEERS)
                .apply()
            current
        }
        val notifManager = NotificationManagerCompat.from(appContext)
        for (peerId in peersToCancel) {
            runCatching { notifManager.cancel(notificationIdForPeer(peerId)) }
        }
        cancelScheduled(appContext)
    }

    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            val request = PeriodicWorkRequestBuilder<ChatNotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }.onFailure {
            Log.w(TAG, "Failed to schedule chat notification worker", it)
        }
    }

    fun cancelScheduled(context: Context) {
        runCatching {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
        }.onFailure {
            Log.w(TAG, "Failed to cancel chat notification worker", it)
        }
    }
}

class ChatNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val social = Social.get(applicationContext)
        if (!social.configured || social.session.value == null) {
            ChatNotifications.cancelScheduled(applicationContext)
            return Result.success()
        }
        if (!ChatNotifications.canPost(applicationContext)) {
            return Result.success()
        }
        runCatching {
            social.loadChatInbox(awaitNotifications = true)
        }.onFailure {
            Log.w("ChatNotificationWorker", "Periodic inbox poll failed", it)
        }
        return Result.success()
    }
}
