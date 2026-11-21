package com.example.myapplication.data.social

import com.example.myapplication.data.SharedLinks
import com.example.myapplication.data.SoundCloudPlaylist
import com.example.myapplication.data.SoundCloudTrack
import com.example.myapplication.data.artistNames
import com.example.myapplication.data.spotifyId
import com.example.myapplication.data.youTubeVideoId
import com.google.gson.annotations.SerializedName

/** What a user sends into a friend chat: a track or album with a service link. */
data class ChatSharePayload(
    val kind: String,
    val service: String,
    val itemUrl: String,
    val title: String,
    val artist: String = "",
    val coverUrl: String? = null,
    val durationMs: Long? = null,
    val trackCount: Int? = null
) {
    companion object {
        fun fromTrack(track: SoundCloudTrack): ChatSharePayload? {
            if (track.urn?.startsWith("local:") == true || track.liveVideoId != null) return null
            val url = track.spotifyId?.let { "https://open.spotify.com/track/$it" }
                ?: track.youTubeVideoId?.let { "https://music.youtube.com/watch?v=$it" }
                ?: track.urn?.takeIf { it.startsWith("yandex:track:") }?.let { urn ->
                    val ids = urn.removePrefix("yandex:track:").split(':')
                    val album = ids.getOrNull(1)
                    if (album != null) "https://music.yandex.ru/album/$album/track/${ids[0]}" else "https://music.yandex.ru/track/${ids[0]}"
                }
                ?: track.permalinkUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: return null
            val service = SharedLinks.serviceOf(url) ?: return null
            val title = track.title?.takeIf { it.isNotBlank() } ?: return null
            return ChatSharePayload(
                kind = "track",
                service = service,
                itemUrl = url,
                title = title,
                artist = track.artistNames.orEmpty(),
                coverUrl = track.artworkUrl,
                durationMs = track.duration.takeIf { it > 0L },
                trackCount = null
            )
        }

        fun fromSet(
            set: SoundCloudPlaylist,
            link: String? = SharedLinks.linkOf(set),
            artistFallback: String = ""
        ): ChatSharePayload? {
            val url = (link ?: SharedLinks.linkOf(set))
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?.takeUnless { SharedLinks.isPersonalUrl(it) }
                ?: return null
            val service = SharedLinks.serviceOf(url) ?: return null
            val tracks = set.knownTracks
            val title = set.title?.takeIf { it.isNotBlank() } ?: return null
            val artist = artistFallback.takeIf { it.isNotBlank() }
                ?: set.user?.username?.takeIf { it.isNotBlank() }
                ?: tracks.firstNotNullOfOrNull { it.artistNames }
                ?: ""
            val count = maxOf(set.trackCount, tracks.size).takeIf { it > 0 }
            return ChatSharePayload(
                kind = "album",
                service = service,
                itemUrl = url,
                title = title,
                artist = artist,
                coverUrl = set.displayArtworkUrl,
                durationMs = null,
                trackCount = count
            )
        }
    }
}

private val SoundCloudTrack.liveVideoId: String?
    get() = urn?.removePrefix("youtube:live:")?.takeIf { urn?.startsWith("youtube:live:") == true && it.isNotBlank() }

data class ChatReaction(
    @SerializedName("user_id") val userId: String,
    val emoji: String? = null,
    val rating: Int? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class ChatMessage(
    val id: String,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("recipient_id") val recipientId: String? = null,
    val kind: String,
    val service: String,
    @SerializedName("item_url") val itemUrl: String,
    val title: String,
    val artist: String = "",
    @SerializedName("cover_url") val coverUrl: String? = null,
    @SerializedName("duration_ms") val durationMs: Long? = null,
    @SerializedName("track_count") val trackCount: Int? = null,
    @SerializedName("read_at") val readAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val reactions: List<ChatReaction> = emptyList(),
    @SerializedName("sender_nick") val senderNick: String? = null,
    @SerializedName("sender_name") val senderName: String? = null,
    @SerializedName("sender_color") val senderColor: String? = null,
    @SerializedName("sender_avatar_v") val senderAvatarV: Int = 0
) {
    val isAlbum: Boolean get() = kind == "album"
    val createdAtMs: Long? get() = createdAt?.let(::parseTime)
    val senderShownName: String get() = senderName?.takeIf { it.isNotBlank() } ?: senderNick.orEmpty()
}

data class ChatThreadSummary(
    @SerializedName("peer_id") val peerId: String,
    val nick: String,
    val name: String? = null,
    val color: String? = null,
    @SerializedName("avatar_v") val avatarV: Int = 0,
    @SerializedName("unread_count") val unreadCount: Int = 0,
    @SerializedName("last_message") val lastMessage: ChatMessage? = null
) {
    val shownName: String get() = name?.takeIf { it.isNotBlank() } ?: nick
}

data class ChatInbox(
    @SerializedName("unread_total") val unreadTotal: Int = 0,
    @SerializedName("new_items") val newItems: List<ChatMessage> = emptyList(),
    val threads: List<ChatThreadSummary> = emptyList()
)

data class GroupedEmojiReaction(
    val emoji: String,
    val count: Int,
    val mine: Boolean
)

/** Groups non-blank emoji reactions in insertion order, tracking count and whether [myUserId] reacted. */
fun groupedEmojiReactions(reactions: List<ChatReaction>, myUserId: String?): List<GroupedEmojiReaction> {
    if (reactions.isEmpty()) return emptyList()
    val counts = LinkedHashMap<String, Int>()
    val mineSet = HashSet<String>()
    for (reaction in reactions) {
        val emoji = reaction.emoji?.takeIf { it.isNotBlank() } ?: continue
        counts[emoji] = (counts[emoji] ?: 0) + 1
        if (myUserId != null && reaction.userId == myUserId) mineSet += emoji
    }
    return counts.entries.map { (emoji, count) ->
        GroupedEmojiReaction(emoji = emoji, count = count, mine = emoji in mineSet)
    }
}

/**
 * Picks which 1..5 stamp appears on a message bubble: prefer the recipient's rating when looking
 * at an outgoing item, or the other person's reaction first, falling back to any valid rating.
 */
fun displayRatingOf(reactions: List<ChatReaction>, myUserId: String?): Int? {
    val valid = reactions.filter { it.rating != null && it.rating in 1..5 }
    if (valid.isEmpty()) return null
    val peerRating = if (myUserId != null) valid.firstOrNull { it.userId != myUserId }?.rating else null
    return peerRating ?: valid.first().rating
}
