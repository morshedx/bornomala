package com.bornomala.keyboard.transliteration.data.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The official OmicronLab Avro Phonetic rule dictionary, deserialized from the bundled
 * `avro/avrodict.json` classpath resource.
 *
 * Data + scheme are OmicronLab's (Copyright © OmicronLab, http://www.omicronlab.com),
 * adapted via Rifat Nabi's jsAvroPhonetic and Kaustav Das Modak's pyAvroPhonetic (MIT).
 * Only the *data* is bundled; the matching algorithm is reimplemented in [AvroParser].
 *
 * The dictionary is parsed once (it is small, ~50 KB) and the resulting [AvroParser] is a
 * shared singleton, so steady-state typing never touches JSON or I/O.
 */
@Serializable
class AvroDictionary(
    val data: AvroData,
)

@Serializable
class AvroData(
    val patterns: List<AvroPattern>,
    val vowel: String,
    val consonant: String,
    @SerialName("casesensitive") val caseSensitive: String,
    val number: String,
)

@Serializable
class AvroPattern(
    val find: String,
    val replace: String,
    val rules: List<AvroRule> = emptyList(),
)

@Serializable
class AvroRule(
    val replace: String,
    val matches: List<AvroMatch> = emptyList(),
)

@Serializable
class AvroMatch(
    val type: String,
    val scope: String,
    val value: String? = null,
)

/**
 * Loads and caches the bundled Avro dictionary from the classpath.
 *
 * Works both on Android (Java resources packaged into the APK) and on the plain JVM (unit
 * tests), so the engine and its tests stay free of any Android `Context`.
 */
object AvroDictionaryLoader {

    private const val RESOURCE_PATH = "/avro/avrodict.json"

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: AvroDictionary? = null

    fun load(): AvroDictionary {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val text = readResource()
            val dict = json.decodeFromString(AvroDictionary.serializer(), text)
            cached = dict
            return dict
        }
    }

    private fun readResource(): String {
        val stream = AvroDictionaryLoader::class.java.getResourceAsStream(RESOURCE_PATH)
            ?: error("Avro dictionary resource not found at $RESOURCE_PATH")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
