# v2 Phase 16 — Ask about a photo (on-device vision)

Part D of the plan: the user attaches a photo to a question ("What plant is this?", "Explain this error screenshot").
The photo never leaves the phone.

## First: is it even possible?

The plan said: only build this if the model really has an image encoder. Model names don't prove that, so we
read the start of each model file (an HTTP Range request for the first 64 KB, which holds the section list):

| Model | Sections found | Vision? |
|---|---|---|
| Gemma 4 E2B | `tf_lite_vision_encoder`, `tf_lite_vision_adapter`, `tf_lite_end_of_vision`, `tf_lite_audio_encoder_hw`, `tf_lite_prefill_decode`, … | **yes** |
| Qwen3.5 0.8B | `tf_lite_prefill_decode` only | no |

Then we checked the LiteRT-LM 0.19 classes with `javap`: `EngineConfig(…, visionBackend, …, maxNumImages, …)`,
`Content.ImageFile(path)`, `Content.ImageBytes(bytes)`, and `Conversation.sendMessageAsync(Contents)`.

## What changed

| Where | Change |
|---|---|
| `models.json`, `LlmModelInfo` | `supportsImages` (true only for Gemma 4 E2B, from the file check above) |
| `LlmEngine` | `load(…, vision)` loads the image encoder; `generate(prompt, sessionId, imagePath)` sends one picture with the prompt |
| `LiteRtLmEngine` | `visionBackend` + `maxNumImages = 1` only when vision is asked for; a vision engine also serves text, so text after a photo doesn't reload |
| `core:database` | Schema **v7** (AutoMigration 6→7): `messages.imagePath`; `MessageDao.imagePaths()` |
| `data:chat/ChatImageStore.kt` (new) | Copies a picked photo into private storage: decoded at reduced size, scaled to ≤ 1024 px, turned upright from EXIF, saved as JPEG; pure `ImageScaling` maths |
| `OfflineChatRepository` | `attachImage`, `discardImage`, `imagesSupported`; `sendMessage(…, imagePath)`; retry and edit keep the photo; rebuilt history says "[The user attached a photo …]"; photos of deleted chats are cleaned up |
| Chat UI | "Add photo" button (only for vision models) → system photo picker; preview with a remove button; the photo above the question bubble |
| Tests | `ImageScalingTest` (3), repository photo tests (4), ViewModel photo tests (4), `MigrationTest` from v6, a device test that a red picture is called "red" |

## Concepts

**Multimodal = more than one kind of input.** A vision-language model has an *image encoder* that turns pixels into
"image tokens" the language model reads like words. In LiteRT-LM it is a separate part of the file that is only loaded
when `EngineConfig.visionBackend` is set. Loading it costs memory, so PocketMind loads it the first time a photo is
sent, not for every chat.

**The photo picker needs no permission.** `ActivityResultContracts.PickVisualMedia` opens the system picker. The app
receives one content URI for the one photo the user chose, never access to the whole gallery. So there is no
`READ_MEDIA_IMAGES` permission, and the Play Store's photo-permission declaration isn't needed.

**Copy the photo right away.** Access to a picker URI is temporary. The app copies the photo at once into
`filesDir/chat_images/` (private: other apps can't read it, and the backup rules leave it out). It also shrinks it:

1. `inJustDecodeBounds` reads only the size.
2. `inSampleSize` (a power of two) decodes a 12 MP photo at about a quarter of its size, so the full bitmap
   (≈ 48 MB) is never built in memory.
3. One `Matrix` scales the longest side to ≤ 1024 px and rotates the picture upright from its EXIF orientation.
4. The result is saved as a JPEG at quality 85.

**History is text; the live conversation has pixels.** While the chat stays open, the model's conversation contains
the real picture. When the conversation is rebuilt from Room (the app restarted, or memory pressure unloaded the
model), re-encoding every old photo would be slow. So the rebuilt history gets a short note instead:
"[The user attached a photo to this message; it is no longer visible to you.]" The model then knows to say it can't
see the photo any more instead of making up details.

**Cleaning up without breaking Undo.** Deleting a chat removes its messages, but its photo files must stay for a few
seconds in case the user taps "Undo". Cleanup runs later, when a new chat is created: it deletes every stored photo
that no message refers to and that isn't attached-but-unsent right now (the repository remembers those).

## Code walkthrough

```kotlin
// LiteRtLmEngine: the image encoder is part of the engine configuration
EngineConfig(
    modelPath = modelPath,
    backend = backend.toLiteRt(),
    visionBackend = if (vision) backend.toLiteRt() else null,
    maxNumTokens = CONTEXT_WINDOW_TOKENS,
    maxNumImages = if (vision) MAX_IMAGES_PER_PROMPT else null,
    cacheDir = cacheDir,
)

// …and the prompt becomes a list of parts: picture first, then the question
val request = if (imagePath == null) Contents.of(prompt)
              else Contents.of(Content.ImageFile(imagePath), Content.Text(prompt))
conv.sendMessageAsync(request).collect { … }
```

```kotlin
// Already loaded? A vision engine serves text too; only a missing encoder forces a reload.
val sameModel = engine != null && loadedPath == modelPath && loadedPreferGpu == preferGpu
if (sameModel && (loadedVision || !vision)) return@withLock
```

```kotlin
// Repository: a photo needs a model that can read it
if (vision && !settings.supportsImages) return AppResult.Failure(ErrorKind.IMAGES_NOT_SUPPORTED)
```

## Trade-offs

- **One photo per question.** It keeps the encoder's memory small and the UI simple. A second pick replaces the first.
- **Load the encoder on demand.** The first photo question waits for a reload (the same few seconds as a normal model
  load); text-only users never pay the memory cost.
- **Old photos aren't re-read after a rebuild.** It's faster and uses less context, but a follow-up question long
  after the photo may get "I can't see it any more". The user can attach it again.
- **The image token budget (512) is a reserve, not a measurement.** The real number depends on the encoder. It is
  listed as a manual step: measure it with `Conversation.getTokenCount()` on the phone.
- **EXIF via AndroidX.** Rotation is read with `androidx.exifinterface` 1.4.2 (Android Lint rejects the framework
  `android.media.ExifInterface`, which has parsing bugs on older Android versions). One small dependency, consistent
  behaviour on every version. Mirrored orientations (rare, from some front cameras) are not flipped.

## Interview questions

1. **How does a vision-language model read an image?**
   An image encoder (often a ViT) turns the image into embedding vectors ("image tokens"). An adapter projects them
   into the language model's embedding space, where they sit in the sequence next to the text tokens.

2. **Why not always load the vision encoder?**
   It uses extra RAM and load time, and most messages are text. Loading it on demand keeps the common case light. A
   model loaded with vision still answers text, so we never unload it just to save the encoder.

3. **How do you let users pick a photo without storage permissions?**
   Use the Android photo picker (`PickVisualMedia`). It returns a URI for just the selected item, with temporary
   read access. Copy what you need right away.

4. **How do you decode a large image without running out of memory?**
   Read the size with `inJustDecodeBounds`, choose a power-of-two `inSampleSize` so the decoder makes a smaller
   bitmap, then scale exactly. Never decode the full-resolution bitmap just to shrink it.

5. **Why are some photos sideways, and how do you fix it?**
   Cameras often save the sensor's orientation and an EXIF tag that says how to rotate the image. `BitmapFactory`
   ignores that tag, so read it and rotate with a `Matrix`.

6. **How do you keep persisted chats and the model's memory consistent when images are involved?**
   Store the image path with the message (the source of truth). The live session holds the real image. A rebuilt
   session gets text plus an explicit note that a photo was there, so the model doesn't claim to see it.

7. **How do you delete files that belong to deleted rows, when there is an Undo?**
   Don't delete them at delete time. Later, garbage-collect: delete files that no row refers to, except ones that are
   in use but not yet saved (tracked in memory).

8. **What would you test, and where?**
   Pure scaling and rotation maths in JVM tests. Repository rules with fakes: which image reaches the engine, when the
   encoder loads, refusal without a vision model, the rebuilt-history note, and cleanup. ViewModel state for
   attach, replace, remove and refuse. On a device: a real model must call a solid red picture "red".

## To verify on device

Select Gemma 4 E2B. In a new chat, tap the photo icon and pick a photo (for example a plant or a screenshot). Ask
"What is in this photo?". The first time, the model reloads with its image encoder (a few seconds), then it answers.
Ask a follow-up without a photo: it answers at once, with no reload. Switch to Qwen: the photo button is gone.
Engine test: `./gradlew :core:llm:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.vision=true`.
