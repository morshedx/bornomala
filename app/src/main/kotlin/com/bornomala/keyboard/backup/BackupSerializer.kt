package com.bornomala.keyboard.backup

import com.bornomala.keyboard.clipboard.domain.model.ClipboardItem
import com.bornomala.keyboard.settings.domain.model.Settings
import com.bornomala.keyboard.suggestions.data.local.LearnedNgramEntity
import com.bornomala.keyboard.suggestions.data.local.UserDictionaryEntity
import com.bornomala.keyboard.theme.KeyboardFont
import com.bornomala.keyboard.theme.KeyboardTheme
import com.bornomala.keyboard.theme.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts a [BackupData] snapshot to/from JSON bytes (UTF-8). No third-party JSON lib —
 * uses `org.json`, matching the OTA code. Unknown/missing fields fall back to
 * [Settings.DEFAULTS] on decode so an older or partial backup still restores cleanly.
 */
@Singleton
class BackupSerializer @Inject constructor() {

    fun encode(data: BackupData): ByteArray = JSONObject().apply {
        put("schemaVersion", data.schemaVersion)
        put("appVersion", data.appVersion)
        put("createdAt", data.createdAt)
        put("device", data.device)
        put("settings", settingsToJson(data.settings))
        put("dictionary", JSONObject().apply {
            put("words", wordsToJson(data.words))
            put("ngrams", ngramsToJson(data.ngrams))
        })
        put("clipboard", clipsToJson(data.clips))
    }.toString().toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): BackupData {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        return BackupData(
            schemaVersion = root.optInt("schemaVersion", BackupData.SCHEMA_VERSION),
            appVersion = root.optString("appVersion", "?"),
            createdAt = root.optLong("createdAt", 0L),
            device = root.optString("device", "?"),
            settings = settingsFromJson(root.optJSONObject("settings")),
            words = wordsFromJson(root.optJSONObject("dictionary")?.optJSONArray("words")),
            ngrams = ngramsFromJson(root.optJSONObject("dictionary")?.optJSONArray("ngrams")),
            clips = clipsFromJson(root.optJSONArray("clipboard")),
        )
    }

    // --- settings ---------------------------------------------------------------------

    private fun settingsToJson(s: Settings) = JSONObject().apply {
        put("themeMode", s.themeMode.name)
        put("keyboardTheme", s.keyboardTheme.name)
        put("keyboardFont", s.keyboardFont.name)
        put("keyBorder", s.keyBorder)
        put("horizontalGapScale", s.horizontalGapScale.toDouble())
        put("verticalGapScale", s.verticalGapScale.toDouble())
        put("keyLabelScale", s.keyLabelScale.toDouble())
        put("suggestionBarScale", s.suggestionBarScale.toDouble())
        put("bottomGapScale", s.bottomGapScale.toDouble())
        put("keyboardHeightScale", s.keyboardHeightScale.toDouble())
        put("keyPressVibration", s.keyPressVibration)
        put("keyPressSound", s.keyPressSound)
        put("numberRowEnabled", s.numberRowEnabled)
        put("suggestionsEnabled", s.suggestionsEnabled)
        put("autoCorrectEnabled", s.autoCorrectEnabled)
        put("blockOffensiveWords", s.blockOffensiveWords)
        put("clipboardEnabled", s.clipboardEnabled)
        put("autoCapitalization", s.autoCapitalization)
        put("doubleSpacePeriod", s.doubleSpacePeriod)
        put("banglaAutoCommit", s.banglaAutoCommit)
        put("banglaPhoneticSuggestions", s.banglaPhoneticSuggestions)
        put("learnFromTyping", s.learnFromTyping)
        put("volumeKeyCursorControl", s.volumeKeyCursorControl)
    }

    private fun settingsFromJson(o: JSONObject?): Settings {
        val d = Settings.DEFAULTS
        if (o == null) return d
        return Settings(
            themeMode = enumOr(o.optString("themeMode"), d.themeMode),
            keyboardTheme = KeyboardTheme.fromName(o.optString("keyboardTheme")) ?: d.keyboardTheme,
            keyboardFont = KeyboardFont.fromName(o.optString("keyboardFont")) ?: d.keyboardFont,
            keyBorder = o.optBoolean("keyBorder", d.keyBorder),
            horizontalGapScale = o.optDouble("horizontalGapScale", d.horizontalGapScale.toDouble()).toFloat(),
            verticalGapScale = o.optDouble("verticalGapScale", d.verticalGapScale.toDouble()).toFloat(),
            keyLabelScale = o.optDouble("keyLabelScale", d.keyLabelScale.toDouble()).toFloat(),
            suggestionBarScale = o.optDouble("suggestionBarScale", d.suggestionBarScale.toDouble()).toFloat(),
            bottomGapScale = o.optDouble("bottomGapScale", d.bottomGapScale.toDouble()).toFloat(),
            keyboardHeightScale = o.optDouble("keyboardHeightScale", d.keyboardHeightScale.toDouble()).toFloat(),
            keyPressVibration = o.optBoolean("keyPressVibration", d.keyPressVibration),
            keyPressSound = o.optBoolean("keyPressSound", d.keyPressSound),
            numberRowEnabled = o.optBoolean("numberRowEnabled", d.numberRowEnabled),
            suggestionsEnabled = o.optBoolean("suggestionsEnabled", d.suggestionsEnabled),
            autoCorrectEnabled = o.optBoolean("autoCorrectEnabled", d.autoCorrectEnabled),
            blockOffensiveWords = o.optBoolean("blockOffensiveWords", d.blockOffensiveWords),
            clipboardEnabled = o.optBoolean("clipboardEnabled", d.clipboardEnabled),
            autoCapitalization = o.optBoolean("autoCapitalization", d.autoCapitalization),
            doubleSpacePeriod = o.optBoolean("doubleSpacePeriod", d.doubleSpacePeriod),
            banglaAutoCommit = o.optBoolean("banglaAutoCommit", d.banglaAutoCommit),
            banglaPhoneticSuggestions = o.optBoolean("banglaPhoneticSuggestions", d.banglaPhoneticSuggestions),
            learnFromTyping = o.optBoolean("learnFromTyping", d.learnFromTyping),
            volumeKeyCursorControl = o.optBoolean("volumeKeyCursorControl", d.volumeKeyCursorControl),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)

    // --- dictionary -------------------------------------------------------------------

    private fun wordsToJson(words: List<UserDictionaryEntity>) = JSONArray().apply {
        words.forEach { w ->
            put(JSONObject().apply {
                put("word", w.word)
                put("lang", w.lang)
                put("frequency", w.frequency)
                put("lastUsed", w.lastUsed)
                put("prevWord", w.prevWord)
            })
        }
    }

    private fun wordsFromJson(arr: JSONArray?): List<UserDictionaryEntity> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            UserDictionaryEntity(
                word = o.getString("word"),
                lang = o.getString("lang"),
                frequency = o.optInt("frequency", 1),
                lastUsed = o.optLong("lastUsed", 0L),
                prevWord = o.optString("prevWord", ""),
            )
        }
    }

    private fun ngramsToJson(ngrams: List<LearnedNgramEntity>) = JSONArray().apply {
        ngrams.forEach { n ->
            put(JSONObject().apply {
                put("context", n.context)
                put("word", n.word)
                put("lang", n.lang)
                put("frequency", n.frequency)
                put("lastUsed", n.lastUsed)
            })
        }
    }

    private fun ngramsFromJson(arr: JSONArray?): List<LearnedNgramEntity> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LearnedNgramEntity(
                context = o.getString("context"),
                word = o.getString("word"),
                lang = o.getString("lang"),
                frequency = o.optInt("frequency", 1),
                lastUsed = o.optLong("lastUsed", 0L),
            )
        }
    }

    // --- clipboard --------------------------------------------------------------------

    private fun clipsToJson(clips: List<ClipboardItem>) = JSONArray().apply {
        clips.forEach { c ->
            put(JSONObject().apply {
                put("id", c.id)
                put("text", c.text)
                put("pinned", c.pinned)
                put("createdAt", c.createdAt)
            })
        }
    }

    private fun clipsFromJson(arr: JSONArray?): List<ClipboardItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ClipboardItem(
                id = o.optLong("id", 0L),
                text = o.getString("text"),
                pinned = o.optBoolean("pinned", false),
                createdAt = o.optLong("createdAt", 0L),
            )
        }
    }
}
