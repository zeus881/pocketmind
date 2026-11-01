# R8 rules for the PocketMind app.
# Most libraries (Room, Hilt, Compose, kotlinx.serialization, WorkManager) ship their own
# "consumer" rules inside their AARs, so this file only holds rules for code R8 cannot see
# being used — e.g. classes that native code (JNI) looks up by name.
