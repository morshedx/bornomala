# R8 / ProGuard rules for the release build.

# Keep the IME service: referenced from the manifest only, so R8 must not strip it.
-keep class com.bornomala.keyboard.ime.KeyboardImeService { *; }

# Hilt generates components reflectively; standard keep rules.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Room generated implementations.
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# kotlinx.serialization — keep generated serializers + the Avro dictionary model so
# Json.decodeFromString works after R8 (the rule table is bundled as a JSON resource).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.bornomala.keyboard.transliteration.data.engine.** { *; }
-keepclassmembers class com.bornomala.keyboard.transliteration.data.engine.** {
    public static ** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
