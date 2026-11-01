# R8 rules for the PocketMind app.
# Most libraries (Room, Hilt, Compose, kotlinx.serialization, WorkManager, OkHttp) ship their own
# "consumer" rules inside their AARs. This file only covers code R8 cannot see being used.

# --- LiteRT-LM -------------------------------------------------------------------------------
# The AAR ships NO consumer rules (verified: no proguard.txt in litertlm-android-0.19.0.aar).
# Its C++ code calls back into Kotlin through JNI by class/method NAME (LiteRtLmJni callbacks,
# message/response callbacks) and converts objects with Gson reflection. If R8 renamed or
# removed them, generation would crash at runtime in release builds only. Keep the package.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** { native <methods>; }

# --- PdfBox-Android --------------------------------------------------------------------------
# Optional JPEG-2000 codec that PdfBox references but does not need for text extraction.
-dontwarn com.gemalto.jp2.**
# PdfBox loads fonts/encodings and some filters by reflection.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
