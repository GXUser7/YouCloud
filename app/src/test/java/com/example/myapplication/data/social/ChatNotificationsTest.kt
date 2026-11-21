package com.example.myapplication.data.social

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class ChatNotificationsTest {

    private lateinit var previousLocale: Locale

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale("ru"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
    }

    private fun msg(
        id: String,
        senderId: String = "peer-1",
        senderName: String? = "Кирилл",
        senderNick: String? = "kirill",
        kind: String = "track",
        title: String = "SMOKE IT OFF!",
        artist: String = "Lumi Athena",
        readAt: String? = null,
        createdAt: String? = "2026-11-21T16:00:00Z"
    ) = ChatMessage(
        id = id,
        senderId = senderId,
        recipientId = "me-1",
        kind = kind,
        service = "spotify",
        itemUrl = "https://open.spotify.com/track/$id",
        title = title,
        artist = artist,
        readAt = readAt,
        createdAt = createdAt,
        senderNick = senderNick,
        senderName = senderName
    )

    private fun thread(
        peerId: String = "peer-1",
        nick: String = "kirill",
        name: String? = "Кирилл",
        unreadCount: Int = 1
    ) = ChatThreadSummary(
        peerId = peerId,
        nick = nick,
        name = name,
        unreadCount = unreadCount
    )

    @Test
    fun firstLoadOrAccountSwitchSeedsExistingUnreadWithoutNotifying() {
        val m1 = msg("m1", senderId = "peer-1")
        val m2 = msg("m2", senderId = "peer-2", senderName = "Аня")
        val inbox = ChatInbox(
            unreadTotal = 2,
            newItems = listOf(m1, m2),
            threads = listOf(thread("peer-1", unreadCount = 1), thread("peer-2", name = "Аня", unreadCount = 1))
        )

        val initialDecision = planChatNotifications(
            myUserId = "me-1",
            initializedUser = null,
            seenIds = emptyList(),
            notifiedPeers = setOf("old-peer"),
            inbox = inbox,
            activePeerId = null,
            appInForeground = false
        )
        assertTrue(initialDecision.seededOnly)
        assertTrue(initialDecision.toNotify.isEmpty())
        assertEquals(setOf("old-peer"), initialDecision.peersToCancel)
        assertEquals(listOf("m1", "m2"), initialDecision.updatedSeenIds)
        assertTrue(initialDecision.updatedNotifiedPeers.isEmpty())

        val switchedDecision = planChatNotifications(
            myUserId = "me-2",
            initializedUser = "me-1",
            seenIds = listOf("old-msg"),
            notifiedPeers = setOf("peer-1"),
            inbox = inbox,
            activePeerId = null,
            appInForeground = false
        )
        assertTrue(switchedDecision.seededOnly)
        assertTrue(switchedDecision.toNotify.isEmpty())
        assertEquals(setOf("peer-1"), switchedDecision.peersToCancel)
        assertEquals(listOf("m1", "m2"), switchedDecision.updatedSeenIds)
        assertTrue(switchedDecision.updatedNotifiedPeers.isEmpty())
    }

    @Test
    fun newUnreadTrackNotifiesAndFormatsWithAndWithoutArtist() {
        val withArtist = msg("m1", title = "Nightcall", artist = "Kavinsky")
        val inboxWithArtist = ChatInbox(
            unreadTotal = 1,
            newItems = listOf(withArtist),
            threads = listOf(thread("peer-1", unreadCount = 1))
        )

        val decision = planChatNotifications(
            myUserId = "me-1",
            initializedUser = "me-1",
            seenIds = emptyList(),
            notifiedPeers = emptySet(),
            inbox = inboxWithArtist,
            activePeerId = null,
            appInForeground = false
        )

        assertFalse(decision.seededOnly)
        assertEquals(1, decision.toNotify.size)
        assertEquals(setOf("peer-1"), decision.updatedNotifiedPeers)
        assertEquals(listOf("m1"), decision.updatedSeenIds)

        val formatted = formatChatNotification(decision.toNotify.single())
        assertEquals("Кирилл отправил трек", formatted.title)
        assertEquals("Nightcall — Kavinsky", formatted.text)
        assertTrue(formatted.inboxLines.isEmpty())

        val noArtistMsg = msg("m2", title = "Untitled Demo", artist = "")
        val formattedNoArtist = formatChatNotification(
            PeerNotificationPlan(
                peerId = "peer-1",
                senderName = "Кирилл",
                senderAvatarV = 0,
                messages = listOf(noArtistMsg),
                totalUnreadCount = 1
            )
        )
        assertEquals("Кирилл отправил трек", formattedNoArtist.title)
        assertEquals("Untitled Demo", formattedNoArtist.text)
    }

    @Test
    fun newUnreadAlbumFormatsAlbumTitle() {
        val albumMsg = msg(
            id = "a1",
            kind = "album",
            title = "Discovery",
            artist = "Daft Punk"
        )
        val decision = planChatNotifications(
            myUserId = "me-1",
            initializedUser = "me-1",
            seenIds = emptyList(),
            notifiedPeers = emptySet(),
            inbox = ChatInbox(unreadTotal = 1, newItems = listOf(albumMsg), threads = listOf(thread())),
            activePeerId = null,
            appInForeground = false
        )

        val formatted = formatChatNotification(decision.toNotify.single())
        assertEquals("Кирилл отправил альбом", formatted.title)
        assertEquals("Discovery — Daft Punk", formatted.text)
    }

    @Test
    fun reloadingSameInboxDoesNotRenotifyOrCancelActiveNotification() {
        val m1 = msg("m1")
        val inbox = ChatInbox(unreadTotal = 1, newItems = listOf(m1), threads = listOf(thread("peer-1", unreadCount = 1)))

        val decision = planChatNotifications(
            myUserId = "me-1",
            initializedUser = "me-1",
            seenIds = listOf("m1"),
            notifiedPeers = setOf("peer-1"),
            inbox = inbox,
            activePeerId = null,
            appInForeground = false
        )

        assertTrue(decision.toNotify.isEmpty())
        assertTrue(decision.peersToCancel.isEmpty())
        assertEquals(setOf("peer-1"), decision.updatedNotifiedPeers)
    }

    @Test
    fun multipleUnreadTracksFromSameFriendFormatInboxLinesLatestFirst() {
        val older = msg("m1", title = "First Track", artist = "Artist A", createdAt = "2026-11-21T16:00:00Z")
        val newer = msg("m2", title = "Second Track", artist = "Artist B", createdAt = "2026-11-21T16:05:00Z")
        val inbox = ChatInbox(
            unreadTotal = 2,
            newItems = listOf(older, newer),
            threads = listOf(thread("peer-1", unreadCount = 2))
        )

        val decision = planChatNotifications(
            myUserId = "me-1",
            initializedUser = "me-1",
            seenIds = listOf("m1"),
            notifiedPeers = setOf("peer-1"),
            inbox = inbox,
            activePeerId = null,
            appInForeground = false
        )

        assertEquals(1, decision.toNotify.size)
        val plan = decision.toNotify.single()
        assertEquals(listOf("m2", "m1"), plan.messages.map { it.id })

        val formatted = formatChatNotification(plan)
        assertEquals("Кирилл · новых: 2", formatted.title)
        assertEquals("Second Track — Artist B", formatted.text)
        assertEquals(listOf("Second Track — Artist B", "First Track — Artist A"), formatted.inboxLines)
    }

    @Test
    fun activePeerInForegroundSuppressesRecordsSeenAndCancelsExisting() {
        val incoming = msg("m2")
        val inbox = ChatInbox(
            unreadTotal = 1,
            newItems = listOf(incoming),
            threads = listOf(thread("peer-1", unreadCount = 1))
        )

        val decision = planChatNotifications(
            myUserId = "me-1",
            initializedUser = "me-1",
            seenIds = listOf("m1"),
            notifiedPeers = setOf("peer-1"),
            inbox = inbox,
            activePeerId = "peer-1",
            appInForeground = true
        )

        assertTrue(decision.toNotify.isEmpty())
        assertTrue("m2" in decision.updatedSeenIds)
        assertEquals(setOf("peer-1"), decision.peersToCancel)
        assertFalse("peer-1" in decision.updatedNotifiedPeers)
    }

    @Test
    fun activePeerInBackgroundStillNotifies() {
        val incoming = msg("m2")
        val inbox = ChatInbox(
            unreadTotal = 1,
            newItems = listOf(incoming),
            threads = listOf(thread("peer-1", unreadCount = 1))
        )

        val decision = planChatNotifications(
            myUserId = "me-1",
            initializedUser = "me-1",
            seenIds = emptyList(),
            notifiedPeers = emptySet(),
            inbox = inbox,
            activePeerId = "peer-1",
            appInForeground = false
        )

        assertEquals(1, decision.toNotify.size)
        assertEquals("peer-1", decision.toNotify.single().peerId)
        assertEquals(setOf("peer-1"), decision.updatedNotifiedPeers)
    }

    @Test
    fun ownMessagesAndReadMessagesAreIgnored() {
        val ownUnread = msg("own-1", senderId = "me-1", readAt = null)
        val alreadyRead = msg("read-1", senderId = "peer-1", readAt = "2026-11-21T16:01:00Z")
        val inbox = ChatInbox(
            unreadTotal = 0,
            newItems = listOf(ownUnread, alreadyRead),
            threads = listOf(thread("peer-1", unreadCount = 0))
        )

        val decision = planChatNotifications(
            myUserId = "me-1",
            initializedUser = "me-1",
            seenIds = emptyList(),
            notifiedPeers = emptySet(),
            inbox = inbox,
            activePeerId = null,
            appInForeground = false
        )

        assertTrue(decision.toNotify.isEmpty())
        assertTrue(decision.updatedSeenIds.isEmpty())
    }

    @Test
    fun peerWithZeroUnreadCancelsPreviousNotification() {
        val inbox = ChatInbox(
            unreadTotal = 0,
            newItems = listOf(msg("m1", readAt = "2026-11-21T16:10:00Z")),
            threads = listOf(thread("peer-1", unreadCount = 0))
        )

        val decision = planChatNotifications(
            myUserId = "me-1",
            initializedUser = "me-1",
            seenIds = listOf("m1"),
            notifiedPeers = setOf("peer-1"),
            inbox = inbox,
            activePeerId = null,
            appInForeground = false
        )

        assertTrue(decision.toNotify.isEmpty())
        assertEquals(setOf("peer-1"), decision.peersToCancel)
        assertTrue(decision.updatedNotifiedPeers.isEmpty())
    }

    @Test
    fun twoFriendsProduceTwoPlansAndDistinctNotificationIds() {
        val m1 = msg("m1", senderId = "peer-alpha", senderName = "Алиса")
        val m2 = msg("m2", senderId = "peer-beta", senderName = "Боб")
        val inbox = ChatInbox(
            unreadTotal = 2,
            newItems = listOf(m1, m2),
            threads = listOf(
                thread("peer-alpha", nick = "alice", name = "Алиса", unreadCount = 1),
                thread("peer-beta", nick = "bob", name = "Боб", unreadCount = 1)
            )
        )

        val decision = planChatNotifications(
            myUserId = "me-1",
            initializedUser = "me-1",
            seenIds = emptyList(),
            notifiedPeers = emptySet(),
            inbox = inbox,
            activePeerId = null,
            appInForeground = false
        )

        assertEquals(2, decision.toNotify.size)
        assertEquals(setOf("peer-alpha", "peer-beta"), decision.toNotify.map { it.peerId }.toSet())
        assertNotEquals(notificationIdForPeer("peer-alpha"), notificationIdForPeer("peer-beta"))
    }

    @Test
    fun seenIdsFifoEvictionHonorsMaxSeenIds() {
        val inbox = ChatInbox(
            unreadTotal = 2,
            newItems = listOf(msg("m4"), msg("m5")),
            threads = listOf(thread("peer-1", unreadCount = 2))
        )

        val decision = planChatNotifications(
            myUserId = "me-1",
            initializedUser = "me-1",
            seenIds = listOf("m1", "m2", "m3"),
            notifiedPeers = emptySet(),
            inbox = inbox,
            activePeerId = null,
            appInForeground = false,
            maxSeenIds = 3
        )

        assertEquals(listOf("m3", "m4", "m5"), decision.updatedSeenIds)
    }
}
