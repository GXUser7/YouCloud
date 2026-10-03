package com.example.myapplication.data

import com.example.myapplication.i18n.tr
import com.example.myapplication.player.PlayLog
import java.util.Calendar

/** The stretches "Итоги" are counted over, back from now. */
enum class StatsPeriod(private val ru: String, private val ruCard: String, val days: Int?) {
    WEEK("Неделя", "за неделю", 7),
    MONTH("Месяц", "за месяц", 30),
    YEAR("Год", "за год", 365),
    ALL("Всё время", "за всё время", null);

    // In the app's language as it is shown, not as the enum was made: it may change meanwhile.
    val label: String get() = tr(ru)
    val cardLabel: String get() = tr(ruCard)
}

/** "Итоги" of [plays]: how much, whom and what most, from where, and at what time of day. */
class ListeningStats(plays: List<PlayLog.Play>) {
    class Artist(val name: String, val ms: Long, val plays: Int)
    class Track(val title: String, val artist: String, val artwork: String?, val plays: Int, val ms: Long)

    val minutes: Long = plays.sumOf { it.ms } / 60_000
    val trackCount: Int = plays.distinctBy { it.id }.size
    val playCount: Int = plays.size

    // One artist however their name is written: "Radiohead" and "radiohead" are the same band.
    val topArtists: List<Artist> = plays.groupBy { it.artist.trim().lowercase() }
        .map { (_, heard) -> Artist(heard.last().artist.trim(), heard.sumOf { it.ms }, heard.size) }
        .sortedByDescending { it.ms }
    val artistCount: Int = topArtists.size

    val topTracks: List<Track> = plays.groupBy { it.id }
        .map { (_, heard) ->
            val last = heard.last()
            Track(last.title, last.artist, heard.lastOrNull { it.artwork != null }?.artwork, heard.size, heard.sumOf { it.ms })
        }
        .sortedWith(compareByDescending<Track> { it.plays }.thenByDescending { it.ms })

    /** Each service's share of the time, largest first; "" for what is not known. */
    val sources: List<Pair<String, Long>> = plays.groupBy { it.source }
        .map { (source, heard) -> source to heard.sumOf { it.ms } }
        .sortedByDescending { it.second }

    /** Time heard by the hour of the day it began in. */
    val byHour: LongArray = LongArray(24).also { hours ->
        val calendar = Calendar.getInstance()
        plays.forEach { play ->
            calendar.timeInMillis = play.at
            hours[calendar.get(Calendar.HOUR_OF_DAY)] += play.ms
        }
    }

    val isEmpty: Boolean get() = playCount == 0

    companion object {
        fun of(all: List<PlayLog.Play>, period: StatsPeriod, now: Long = System.currentTimeMillis()): ListeningStats {
            val from = period.days?.let { now - it * 86_400_000L } ?: Long.MIN_VALUE
            return ListeningStats(all.filter { it.at >= from })
        }

        /** A service's name as the app writes it. */
        fun sourceName(source: String): String = when (source) {
            "yandex" -> tr("Яндекс")
            "youtube" -> "YouTube"
            "soundcloud" -> "SoundCloud"
            "phone" -> tr("С телефона")
            else -> tr("Скачанное")
        }
    }
}
