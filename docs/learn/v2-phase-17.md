# v2 Phase 17 — Tool calling: calculator, date, units, notes

Part D of the plan. Small language models are good with words and bad with exact facts: they *predict* digits
instead of calculating ("25 + 26 = 52"), and they don't know today's date. Tool calling lets the model hand those jobs
to ordinary code on the phone.

## First: can our models do it?

A model can only call tools if its **chat template** has a format for tool calls. We read the start of each pinned
model file (where the template is stored):

| Model | `tool_call` / `tool_response` in the template | Tools |
|---|---|---|
| Gemma 4 E2B | yes | on |
| Qwen3 0.6B | yes | on |
| Qwen3.5 0.8B | not found in the header | off |

Then we checked LiteRT-LM 0.19 with `javap`. `ConversationConfig(…, tools: List<ToolProvider>, …,
automaticToolCalling: Boolean, …)`; `OpenApiTool { getToolDescriptionJsonString(); execute(paramsJson): String }`;
and `tool(OpenApiTool): ToolProvider`. The JSON format of a description was taken from the library's own reflection
tool (its bytecode has the keys `name`, `description`, `parameters`, `type`, `properties`, `required`).

## What changed

| Where | Change |
|---|---|
| `core:tools` (new, pure Kotlin) | `LocalTool`, `ToolSchema` (JSON description), `ToolRunner` (errors become `{"error": …}`), `CalculatorTool` (exact BigDecimal parser), `DateTimeTool`, `UnitConverterTool` (exact international factors), `NotesTool` + `NoteStore` |
| `LlmEngine.newSession(…, tools)` | `LiteRtLmEngine` wraps each tool as an `OpenApiTool` with `automaticToolCalling = true` and records the calls; `GenerationStats.toolsUsed` |
| `models.json` | `supportsTools` (Gemma 4 E2B, Qwen3 0.6B) |
| `core:database` | Schema **v8** (AutoMigration 7→8): `notes` table, `chats.toolsEnabled` (default on), `messages.toolsUsed` |
| `data:chat` | `ChatTools` (the four tools; notes in Room); the repository offers tools only when the model supports them and the chat allows them; a short "when to use which tool" rule is added to the system prompt; the tool declarations count against the context budget |
| `data:chat` interfaces | `ChatModel` (model state, loading, abilities, stats) split from `ChatRepository`, like `ChatPhotos` |
| Chat UI | a Tools switch in the top bar (only for tool models); "Used: Calculator" under answers that used a tool |
| Tests | 11 tool tests without a model; repository tests where the fake engine "calls" real tools; ViewModel tests for the switch; migration from v7 |

## Concepts

**How tool calling works.**
1. The app describes each tool in JSON (name, when to use it, parameters).
2. The runtime puts these descriptions into the prompt using the model's chat template.
3. When the model wants a tool, it writes a structured call (`tool_call: calculator {"expression": "123.4*56"}`)
   instead of text.
4. With `automaticToolCalling`, LiteRT-LM spots the call, runs our `execute()`, and puts the result back into the
   conversation as a `tool_response`.
5. The model keeps writing and uses the exact result. The user sees only the final answer, plus our "Used:" chip.

**Exact arithmetic needs exact numbers.** `0.1 + 0.2` in `Double` is `0.30000000000000004`. The calculator parses
into `BigDecimal`, so `+ − ×` are exact and division uses 34 significant digits. Unit factors are the exact legal
definitions (1 in = 0.0254 m exactly), so `1 in = 2.54 cm` comes out exactly too.

**A recursive-descent parser in 100 lines.** One function per precedence level (`sum → product → unary → power →
postfix → primary`). Each calls the next-higher level, which gives correct precedence for free. `-3^2` is −9 because
unary minus sits *below* power. `2^3^2` = 2⁹ because `power` recurses to the right.

**Never let a tool crash a generation.** The model writes the arguments, so they can be anything: missing fields,
`"ten"` instead of 10, invalid JSON. `ToolRunner` turns every such problem into `{"error": "'value' must be a number,
got 'ten'"}`. The model reads that and usually corrects itself on the next try.

**Tools cost context.** The JSON declarations are part of the prompt (a few hundred tokens), so the repository
subtracts them from the context budget. A chat can switch tools off: for creative writing they only add tokens and the
chance of a pointless call.

**Interface segregation, again.** `ChatRepository` had grown to 20 functions. The chat screen alone needs the model
functions (state, load, abilities, stats), so those moved to `ChatModel`. One object still implements all three
interfaces, because they share state; each screen sees only what it uses.

## Code walkthrough

```kotlin
// LiteRtLmEngine: our tool → LiteRT-LM's OpenApiTool
private fun LocalTool.toLiteRt(): ToolProvider = tool(
    object : OpenApiTool {
        override fun getToolDescriptionJsonString(): String = ToolSchema.describe(this@toLiteRt)
        override fun execute(paramsJsonString: String): String {
            toolCalls += name                      // for the "Used:" chip
            return ToolRunner.run(this@toLiteRt, paramsJsonString)
        }
    },
)
// newSession: ConversationConfig(…, tools = tools.map { it.toLiteRt() }, automaticToolCalling = true, …)
```

```kotlin
// Calculator: precedence by recursion
fun sum(): BigDecimal { var v = product(); while (true) v = when { take('+') -> v.add(product()); take('-') -> v.subtract(product()); else -> return v } }
private fun unary(): BigDecimal = when { take('-') -> unary().negate(); take('+') -> unary(); else -> power() }
private fun power(): BigDecimal { val base = postfix(); return if (take('^')) raise(base, unary()) else base }
```

```kotlin
// Repository: tools only where they make sense
private suspend fun toolsFor(chatId: String, settings: InferenceSettings): List<LocalTool> =
    if (settings.supportsTools && chatDao.get(chatId)?.toolsEnabled != false) tools.all else emptyList()
```

## Trade-offs

- **Automatic tool calling.** The runtime runs the loop (call → result → continue), which keeps our code small. We
  can't show "calculating…" live; the chip appears with the finished answer.
- **Blocking tools.** `execute()` is synchronous, so the notes tool uses blocking Room calls. That is fine on the
  engine's background thread, and Room still refuses them on the main thread.
- **Notes have no screen of their own yet.** The user manages them through chat ("list my notes", "forget note …").
  The data export and "delete all my data" in phase 18 include them.
- **The model decides when to call a tool.** A 2B model may still skip the calculator now and then. The system-prompt
  rule ("use the calculator for every calculation") makes calls much more likely, but can't guarantee them.
- **Qwen3.5 gets no tools.** Its template wasn't found in the file header, so we don't claim support we haven't seen.

## Interview questions

1. **What is LLM tool calling, step by step?**
   Describe the tools (JSON schema) in the prompt. The model emits a structured call. The host runs the function and
   returns the result as a tool message. The model continues with the result. Repeat until the model writes a normal
   answer.

2. **Why does a calculator tool fix arithmetic mistakes?**
   The model predicts tokens; it doesn't compute. With a tool it only has to write the expression, which it is good
   at, and copy an exact result.

3. **Why BigDecimal instead of Double?**
   Binary floating point can't represent most decimal fractions (0.1), so sums drift. BigDecimal stores decimal digits
   exactly; only division and roots need a chosen precision.

4. **How does a recursive-descent parser get operator precedence right?**
   Each grammar level is a function that calls the next-higher-precedence level for its operands. Loops handle
   left-associative operators; recursion on the right handles right-associative ones (`^`).

5. **How do you make tools robust to bad model output?**
   Validate every argument, accept common variants ("1,000", "km/h", "°C"), and return errors as data the model can
   read. Never throw into the runtime.

6. **What do tools cost, and how do you control it?**
   Context tokens for the declarations, latency for each call, and the risk of unnecessary calls. Offer them only to
   models trained for them, count them in the budget, and let users switch them off per chat.

7. **How do you test tool calling without the model?**
   Unit-test each tool directly. Let a fake engine "call" the real tools with scripted arguments, and assert what the
   repository stores (tools used, notes saved). Check the real model's behaviour on a device.

8. **Why split ChatRepository into several interfaces implemented by one class?**
   Clients depend only on what they use (interface segregation). The shared state (sessions, pending photos) stays in
   one place, and fakes and tests stay small.

## To verify on device

Gemma 4 E2B, Tools on (the calculator icon in the chat's top bar is highlighted). Try these:
- "What is 123.4 × 56?" → 6910.4, with "Used: Calculator".
- "What day is it today?" → the phone's date, with "Used: Date and time".
- "Convert 10 km to miles" → 6.21… mi.
- "Remember my locker code is 4312", then in a new chat "What is my locker code?" → 4312 (Notes).

Tap the icon to switch Tools off: the next answer comes without tools. Engine check:
`./gradlew :core:llm:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.tools=true`.
