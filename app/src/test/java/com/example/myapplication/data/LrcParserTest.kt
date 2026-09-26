package com.example.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesYandexLrc() {
        val lines = YandexLyricsRepository.parseLrc(
            """
            [00:00.91] Эй
            [00:04.37] Мне не нужны
            [00:08.41]
            [01:11.8] We are
            """.trimIndent()
        )
        assertEquals(
            listOf(
                LyricLine(910, "Эй"),
                LyricLine(4_370, "Мне не нужны"),
                LyricLine(8_410, ""),
                LyricLine(71_800, "We are")
            ),
            lines
        )
    }

    @Test
    fun repeatedTagsAndMetadata() {
        val lines = YandexLyricsRepository.parseLrc(
            """
            [ar:Someone]
            [00:20.000][00:05.500] Chorus
            """.trimIndent()
        )
        assertEquals(listOf(LyricLine(5_500, "Chorus"), LyricLine(20_000, "Chorus")), lines)
    }
}
