package com.example.myapplication.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * "Моя волна" — Yandex Music's endless personal radio — and what it can be tuned by: settings
 * (mood, character, language), each a choice of values, and occasions (working out, falling
 * asleep…). Every value is a seed added to the wave's session, as the Yandex app adds it.
 */
data class YandexWaveSettings(
    val groups: List<YandexWaveGroup>,
    val occasions: List<YandexWaveOption>
)

/** One setting: its values, of which one or none is picked. [key] as Rotor names it (`moodEnergy`…). */
data class YandexWaveGroup(val key: String, val title: String, val options: List<YandexWaveOption>)

/** A value of a setting, or an occasion: [seed] is what the session is given for it. */
data class YandexWaveOption(val seed: String, val title: String, val value: String)

object YandexWave {
    /** The wave's own seed: the listener's taste. */
    const val SEED = "user:onyourwave"

    const val MOOD = "moodEnergy"
    const val DIVERSITY = "diversity"
    const val LANGUAGE = "language"

    private val ORDER = listOf(MOOD, DIVERSITY, LANGUAGE)

    /**
     * What the Yandex app offers, for when Rotor's own list doesn't come: its values and seeds
     * as Rotor serialises them (`settingDiversity:favorite`).
     */
    val DEFAULT = YandexWaveSettings(
        groups = listOf(
            group(MOOD, "Настроение", "active" to "Бодрое", "fun" to "Весёлое", "calm" to "Спокойное", "sad" to "Грустное"),
            group(DIVERSITY, "Характер", "favorite" to "Любимое", "discover" to "Незнакомое", "popular" to "Популярное"),
            group(LANGUAGE, "Язык", "russian" to "Русский", "not-russian" to "Иностранный", "without-words" to "Без слов")
        ),
        occasions = emptyList()
    )

    private fun group(key: String, title: String, vararg values: Pair<String, String>) = YandexWaveGroup(
        key = key,
        title = title,
        options = values.map { (value, name) -> YandexWaveOption(settingSeed(key, value), name, value) }
    )

    private fun settingSeed(key: String, value: String) =
        "setting" + key.replaceFirstChar { it.uppercase() } + ":" + value

    /**
     * Reads `rotor/wave/settings`: `settingRestrictions` holds the settings, each with its
     * `possibleValues` (the "any" one left out: none picked is any), and `blocks` of the `contexts`
     * type the occasions, stations whose id is their seed. Whatever doesn't read is taken from
     * [DEFAULT], group by group.
     */
    fun parse(json: JsonObject): YandexWaveSettings {
        val result = json.obj("result") ?: json
        val restrictions = result.obj("settingRestrictions") ?: result.obj("restrictions2")
        val read = restrictions?.entrySet().orEmpty().mapNotNull { (key, element) ->
            val setting = element.asObjectOrNull() ?: return@mapNotNull null
            val options = setting.array("possibleValues").mapNotNull { valueElement ->
                val value = valueElement.asObjectOrNull() ?: return@mapNotNull null
                if (value.bool("unspecified") == true) return@mapNotNull null
                val raw = value.string("value") ?: return@mapNotNull null
                YandexWaveOption(
                    seed = value.string("serializedSeed") ?: settingSeed(key, raw),
                    title = value.string("name") ?: raw,
                    value = raw
                )
            }
            if (options.isEmpty()) return@mapNotNull null
            // Named short, as a row's label: Rotor's own names are "Под настроение", "По характеру".
            YandexWaveGroup(key, DEFAULT.groups.firstOrNull { it.key == key }?.title ?: setting.string("name") ?: key, options)
        }
        val groups = (read + DEFAULT.groups.filter { default -> read.none { it.key == default.key } })
            .filter { it.key in ORDER }
            .sortedBy { ORDER.indexOf(it.key) }
        val occasions = result.array("blocks")
            .mapNotNull { it.asObjectOrNull() }
            .flatMap { block -> block.array("items") }
            .mapNotNull { item ->
                val station = item.asObjectOrNull()?.let { it.obj("station") ?: it } ?: return@mapNotNull null
                val id = station.obj("id") ?: return@mapNotNull null
                val type = id.string("type") ?: return@mapNotNull null
                val tag = id.string("tag") ?: return@mapNotNull null
                YandexWaveOption(seed = "$type:$tag", title = station.string("name") ?: tag, value = tag)
            }
            .distinctBy { it.seed }
        return YandexWaveSettings(groups, occasions)
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
    private fun JsonObject.obj(name: String): JsonObject? = get(name)?.asObjectOrNull()
    private fun JsonObject.array(name: String): List<JsonElement> =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString
    private fun JsonObject.bool(name: String): Boolean? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
}
