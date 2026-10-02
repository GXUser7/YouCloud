package com.example.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedLinksTest {
    private fun parse(url: String) = SharedLinks.parse(url)

    @Test
    fun findsTheLinkInsideSharedWords() {
        assertEquals(
            "https://music.yandex.ru/album/3542/track/1710811",
            SharedLinks.findUrl("Слушайте «Creep» в Яндекс Музыке: https://music.yandex.ru/album/3542/track/1710811.")
        )
        assertNull(SharedLinks.findUrl("ничего тут нет"))
    }

    @Test
    fun yandexLinks() {
        assertEquals(SharedLink.YandexTrack("1710811", "3542"), parse("https://music.yandex.ru/album/3542/track/1710811?utm_source=desktop"))
        assertEquals(SharedLink.YandexTrack("1710811", null), parse("https://music.yandex.com/track/1710811"))
        assertEquals(SharedLink.YandexAlbum(3542), parse("https://music.yandex.ru/album/3542"))
        assertEquals(SharedLink.YandexArtist("36800"), parse("https://music.yandex.ru/artist/36800/tracks"))
        assertEquals(
            SharedLink.YandexPlaylist("yamusic-bestsongs", "36825"),
            parse("https://music.yandex.ru/users/yamusic-bestsongs/playlists/36825")
        )
        assertEquals(
            SharedLink.YandexPlaylistUuid("ar.273c2610-f97c-452c-9b76-6ffe77b2f5ce"),
            parse("https://music.yandex.ru/playlists/ar.273c2610-f97c-452c-9b76-6ffe77b2f5ce?utm_medium=copy_link")
        )
        assertNull(parse("https://music.yandex.ru/home"))
    }

    @Test
    fun youTubeLinks() {
        assertEquals(SharedLink.YouTubeVideo("dQw4w9WgXcQ"), parse("https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=abc"))
        assertEquals(SharedLink.YouTubeVideo("dQw4w9WgXcQ"), parse("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals(SharedLink.YouTubeVideo("dQw4w9WgXcQ"), parse("https://m.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1"))
        assertEquals(SharedLink.YouTubeVideo("jfKfPfyJRdk"), parse("https://www.youtube.com/live/jfKfPfyJRdk?si=x"))
        assertEquals(
            SharedLink.YouTubeSet("VLOLAK5uy_kx", "OLAK5uy_kx"),
            parse("https://music.youtube.com/playlist?list=OLAK5uy_kx&si=q")
        )
        assertEquals(SharedLink.YouTubeSet("", "RDCLAK5uy_mix"), parse("https://music.youtube.com/playlist?list=RDCLAK5uy_mix"))
        assertEquals(SharedLink.YouTubeSet("MPREb_abc", ""), parse("https://music.youtube.com/browse/MPREb_abc"))
        assertEquals(SharedLink.YouTubeChannel("UCq19-LqvG35A-30oyAiPiqA"), parse("https://music.youtube.com/channel/UCq19-LqvG35A-30oyAiPiqA?si=z"))
        assertNull(parse("https://www.youtube.com/@radiohead"))
    }

    @Test
    fun soundCloudLinks() {
        assertEquals(
            SharedLink.SoundCloud("https://soundcloud.com/radiohead/creep"),
            parse("https://soundcloud.com/radiohead/creep?si=0a1b&utm_source=clipboard")
        )
        assertEquals(SharedLink.SoundCloud("https://soundcloud.com/radiohead/sets/ok-computer"), parse("https://m.soundcloud.com/radiohead/sets/ok-computer"))
        assertEquals(SharedLink.SoundCloud("https://soundcloud.com/radiohead"), parse("https://soundcloud.com/radiohead"))
        assertEquals(SharedLink.Short("https://on.soundcloud.com/AbCd1"), parse("https://on.soundcloud.com/AbCd1"))
        assertNull(parse("https://soundcloud.com/discover"))
        assertNull(parse("https://example.com/radiohead/creep"))
    }
}
