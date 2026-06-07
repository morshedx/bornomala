# Transliteration module exposes only stable Kotlin/Hilt types; no reflection-based
# serialization is used. Keep public engine + result model intact for consumers.
-keep interface com.bornomala.keyboard.transliteration.domain.engine.TransliterationEngine { *; }
-keep class com.bornomala.keyboard.transliteration.domain.model.** { *; }
