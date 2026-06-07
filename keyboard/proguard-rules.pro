# Module-local R8/ProGuard rules. The IME service must survive shrinking because it is
# instantiated reflectively by the Android framework from the manifest declaration.
-keep class com.bornomala.keyboard.ime.KeyboardImeService { *; }
