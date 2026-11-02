# v2 Phase 15 — Voice in, voice out (on the device only)

Part D of the plan. Voice is free, per the approved decision (it's an accessibility feature).

## What changed

| Where | Change |
|---|---|
| `core:speech` (new module) | `SpeechInput` / `SpeechOutput` interfaces; `AndroidSpeechInput` (on-device `SpeechRecognizer`, Android 12+); `AndroidSpeechOutput` (TextToSpeech with offline voices only); pure `SpeechText` (Markdown → speakable text, chunking) and `SpeechErrors` |
| `core:speech` manifest | `RECORD_AUDIO`; `<queries>` for `RecognitionService` and `TTS_SERVICE` (Android 11 package visibility) |
| `ChatViewModel` | `startVoice(languageTag)`, `stopVoice()`, `toggleSpeak(id, text)`; `VoiceUi` state; speech stops when the screen closes |
| Chat UI | Mic button (only if an on-device recognizer exists); the live transcript fills the input, the **user** sends; "Read aloud / Stop reading" on every answer; a message per voice problem (en + hi) |
| Tests | `SpeechTextTest` (4), voice VM tests (4) with fake speech input/output |

## Concepts

**Privacy decides the API.** Android has two ways to recognise speech:
- `SpeechRecognizer.createOnDeviceSpeechRecognizer` (Android 12+): guaranteed on-device.
- The classic recognizer with `EXTRA_PREFER_OFFLINE`: a *preference*. If the offline language pack is missing it can
  silently use Google's servers.

PocketMind promises that nothing leaves the phone, so only the first is used. On Android 8–11 the mic button simply
isn't shown. Text to speech follows the same rule: only voices with `isNetworkConnectionRequired == false`.

**Callback APIs → Flow.** The recognizer reports through a listener on the main thread. `callbackFlow { … }` turns the
callbacks into a cold Flow (`trySend` for each event), and `awaitClose { recognizer.cancel(); recognizer.destroy() }`
guarantees cleanup when the collector stops: Stop button, screen closed, or ViewModel cleared.
`flowOn(Dispatchers.Main.immediate)` keeps the recognizer on the main thread as it requires.

**The user stays in control.** Speech fills the input box (partial results update it live); the user reads, edits,
and taps Send. The model never receives text the user didn't confirm. The same rule applies to shared text
(phase 13).

**Make Markdown speakable.** A TTS engine reads `**` and code literally. `SpeechText.fromMarkdown` drops formatting,
keeps link labels, and replaces code blocks with a short note. Engines also refuse very long inputs
(`getMaxSpeechInputLength`, often 4,000 characters), so `chunks` splits at sentence ends and queues the pieces.

**Package visibility.** Since Android 11, an app can't see other apps' services unless it declares what it looks for
in `<queries>`. Without the two `<intent>` entries, both the recognizer and the TTS engine look "unavailable" even
though they're installed. That's a classic "works on my Android 10 phone" bug.

**Runtime permission flow.** The mic asks for `RECORD_AUDIO` the first time (`RequestPermission` contract). Granted →
start listening; denied → nothing happens, and the next tap asks again (Android shows the rationale rules).

## Code walkthrough

```kotlin
override fun listen(languageTag: String): Flow<SpeechEvent> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !isAvailable) return flowOf(SpeechEvent.Error(UNAVAILABLE))
    return callbackFlow {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(b: Bundle?) { b.firstResult()?.let { trySend(SpeechEvent.Partial(it)) } }
            override fun onResults(b: Bundle?) { trySend(…Final…); close() }
            override fun onError(error: Int) { trySend(SpeechEvent.Error(SpeechErrors.map(error))); close() }
            …
        })
        recognizer.startListening(Intent(ACTION_RECOGNIZE_SPEECH).putExtra(EXTRA_PARTIAL_RESULTS, true) …)
        awaitClose { recognizer.cancel(); recognizer.destroy() }
    }.flowOn(Dispatchers.Main.immediate)
}
```

```kotlin
// Offline voices only
val offline = engine.voices.orEmpty().filter { !it.isNetworkConnectionRequired }
val voice = offline.firstOrNull { it.locale.language == language } ?: offline.firstOrNull() ?: return false
```

```kotlin
// ChatInput: a new transcript replaces the box's text; afterwards it's the user's to edit
LaunchedEffect(voice.transcriptVersion) { voice.transcript?.let { text = it } }
```

## Trade-offs

- **No voice on Android 8–11.** Fewer users get voice, but the privacy promise holds for everyone. The UI hides the mic
  instead of showing a broken one.
- **Recognition quality depends on the phone's speech service** and its downloaded languages (Hindi needs its offline
  pack). We explain how to install it instead of failing silently.
- **Transcript into the box, not auto-send.** One extra tap, but no surprise prompts to the model.

## Interview questions

1. **How do you guarantee speech recognition stays on the device?**
   Use `SpeechRecognizer.createOnDeviceSpeechRecognizer` (API 31+) and check `isOnDeviceRecognitionAvailable`.
   `EXTRA_PREFER_OFFLINE` is only a hint and can fall back to the network.

2. **How do you wrap a listener-based Android API in coroutines?**
   `callbackFlow`: register the listener, `trySend` events, `close()` on completion, and release resources in
   `awaitClose`. Collectors get cancellation for free.

3. **Why must the recognizer run on the main thread, and how do you enforce it?**
   `SpeechRecognizer` must be created and called from the main thread (it binds to a service and uses a Handler). Put
   the flow on `Dispatchers.Main.immediate` with `flowOn`.

4. **What is Android 11 package visibility, and how did it affect this feature?**
   Apps can't see other packages or services unless declared in `<queries>`. Without the `RecognitionService` and
   `TTS_SERVICE` queries, recognition and TTS appear unavailable.

5. **How do you make Markdown suitable for TTS?**
   Strip formatting characters, keep link text, replace code blocks with a spoken note, and split long text into
   chunks under the engine's input limit.

6. **How do you test speech features without a microphone?**
   Put speech behind interfaces and use fakes that emit scripted events. Test pure transformations (Markdown → speech,
   chunking, error mapping) directly. Check real recognition on a device.

7. **Why not send the transcript automatically?**
   Recognition makes mistakes; auto-sending would put misheard text into the model's context. Letting the user confirm
   keeps the conversation accurate and the user in control.

8. **When should a feature be hidden vs disabled?**
   Hide it when it can never work in the current environment (no on-device recognizer). Disable it with an
   explanation when it's temporarily unavailable (generation running). That avoids dead buttons and confusion.

## To verify on device (Android 12+)

Airplane mode on. Tap the mic in a new chat, speak a question: words appear in the box; tap Send. Tap "Read aloud"
under an answer: it's spoken offline; tap again to stop. In Hindi, install the offline Hindi speech pack first.
