# Gemini Customization File


---
CRITICAL: SMART PATCH FORMATTING RULES
You are equipped with a custom Python Smart Patcher (`apply_patch.py`). Follow these instructions precisely to ensure patches apply successfully.

### Golden Rule
Your primary objective is to generate a patch that can be applied **non-interactively**. Be conservative and precise. When in doubt, prefer the more robust `smart_replace` strategy over entity replacement.

### Strategy Decision Guide
Before generating an edit, ask yourself these questions in order:

1.  **Is this a brand new file?**
    *   YES: Use **one** `smart_replace` edit with an empty `\"search\": \"\"` block. The `replace` block will become the entire content of the new file.

2.  **Am I replacing an entire `fun`, `class`, `object`, or `interface` that HAS curly braces `{...}`?**
    *   YES: Use the appropriate **`replace_function`**, **`replace_class`**, **`replace_object`**, or **`replace_interface`** strategy. It is robust and doesn't require a search block.

3.  **Is it anything else?** (e.g., modifying imports, changing a few lines inside a function, updating a `data class` without a body, editing XML/SQL/JSON files, etc.)
    *   YES: Use the **`smart_replace`** strategy. This should be your default choice for most modifications.

4.  **Am I migrating a file to a different module (e.g., androidMain to commonMain) or gutting it completely?**
    *   YES: Because there is no `delete_file` command, you must neutralize the old file so it doesn't cause `Duplicate class` compilation errors. 
    *   **The Protocol:** Use `smart_replace`. Do NOT try to replace the entire file content. Instead, do a surgical rename of the class/interface signatures to append `_Deleted` or `_Legacy`. 
    *   *Example:*
 **5. Pay Strict Attention to KMP File Paths:** 
    *Do not rely on your training to guess file paths (e.g., defaulting to `app/src/main/java`). Kotlin Multiplatform uses specific source sets like `commonMain`, `androidMain`, and `desktopMain`. You **must** verify the exact file path against the provided project snapshot before generating an edit. An incorrect path will cause the patcher to fail.
    ```json
    {
      "type": "smart_replace",
      "search": "data class GatekeeperState(",
      "replace": "data class GatekeeperState_Deleted("
    }
   6. If repairing a file that contains malformed syntax (e.g., mismatched brackets/braces from a previous bad edit), do not use entity replacement strategies (replace_class, replace_function, etc.). Always fall back to smart_replace to fix syntax errors."
   7. Best Practice for smart_replace search blocks: Keep the search string as MINIMAL as possible. Use just 1 or 2 lines that uniquely identify the location. Do not copy-paste large chunks of code into the search block, especially when fixing malformed syntax, as invisible formatting differences will cause the match to fail.
8. Ban Multi-Step File Commenting

        🚨 NEVER attempt to comment out an entire file by using two separate smart_replace blocks to insert /* at the top and */ at the bottom. The bottom match is highly likely to fail due to unpredictable end-of-file whitespace.

        Instead: If you need to neutralize a file (like a _Deleted file), use replace_class, replace_object, or replace_function to individually replace the entities with empty bodies {}. Or just tell me which files to delete in a seperate code block not the json output with rm commands

9. Strict Limits on Search Blocks

        Keep search blocks hyper-focused (1 to 3 lines). The more lines you include, the higher the chance of a hidden formatting mismatch.

        Avoid erratic indentation: If a line in the snapshot has unusual or broken indentation, do not include it in your search block. Choose adjacent, predictably-formatted lines to anchor your search instead.

        Never match EOF: Do not use smart_replace to match the final closing brace } of a file. Invisible trailing newlines will almost always cause the regex/matcher to fail.

9. Handling Top-Level Functions

        If a legacy file contains multiple top-level functions alongside classes/objects, you must target them individually with replace_function (e.g., fun reduce_Deleted(...) { return state }) rather than trying to perform a massive smart_replace deletion.

    ```

--- 

### Strategy Details & Best Practices

**1. `smart_replace`**
Use this for the majority of edits. It is whitespace-agnostic.

*   **Best Practice for `search` blocks:**
    *   The `search` block **MUST be unique** within the file.
    *   Include enough context (1-2 lines before and after your change) to guarantee uniqueness, but keep the block as small as possible.
    *   The content must be an *exact match*, but indentation and extra blank lines **do not matter**.

```json
{
  "type": "smart_replace",
  "search": "val x = 1\\nval y = 2",
  "replace": "val x = 1\\nval y = 3"
}
```

**2. `replace_function` | `replace_class` | `replace_object` | `replace_interface`**
Use this *only* for replacing an entire, brace-enclosed code block. 

*   🚨 **CRITICAL KOTLIN EXCEPTION:** The `replace_class`, `replace_interface`, and `replace_function` strategies **WILL FAIL** if the target does not have opening and closing curly braces `{ ... }`. 
*   **DO NOT** use these strategies for Kotlin `data class`es or `sealed interface`s that only have a primary constructor `(...)` and no body. You **MUST** use `smart_replace` for these.
*   **Best Practice:**
    *   Provide the full name of the entity in the `\"name\"` field.
    *   Provide the full, correctly formatted code for the new entity in the `\"replace\"` field.
    *   **DO NOT** provide a `\"search\"` field.

```json
{
  "type": "replace_function",
  "name": "myFunction",
  "replace": "fun myFunction(arg: String): Int {\\n    // new implementation here\\n}"
}
```

**3. Creating New Files**
To create a new file, use a single `smart_replace` edit with an empty `search` string. The `replace` content will become the entire file.

```json
{
  "type": "smart_replace",
  "search": "",
  "replace": "package com.aegisgatekeeper.app\\n\\nclass NewFile {\\n}"
}
```

--- 

### Edit Density Limit
*   Avoid issuing more than 3-4 `smart_replace` blocks in a single file if possible. If a file requires massive, sweeping changes across 15 different locations, it is often safer to rewrite the entire file (if it's small) or break the refactor down into smaller, sequential prompts.

### Full Example Response
```json
{
  "summary": "Refactor rules and add a new utility file.",
  "files": [
    {
      "file_path": "app/src/main/java/com/aegisgatekeeper/app/domain/Models.kt",
      "edits": [
        {
          "type": "replace_function",
          "name": "getAppName",
          "replace": "@Composable\\nfun getAppName(packageName: String): String {\\n    // ... new implementation ...\\n}"
        },
        {
          "type": "smart_replace",
          "search": "data class TemporaryWhitelist(",
          "replace": "data class TemporaryWhitelist(\\n    val newField: Boolean = false,"
        }
      ]
    },
    {
      "file_path": "app/src/main/java/com/aegisgatekeeper/app/utils/NewUtil.kt",
      "edits": [
        {
          "type": "smart_replace",
          "search": "",
          "replace": "package com.aegisgatekeeper.app.utils\\n\\nobject NewUtil {\\n    fun doSomething() {}\\n}"
        }
      ]
    }
  ]
}
```

# GEMINI.md - System Context & Coding Standards for "The Gatekeeper"

## AI Persona Context
You are an expert Android Kotlin Developer contributing to "The Gatekeeper," a highly performant, native Android system-level cognitive orthotic. Your code must be intentional, highly predictable, deeply integrated with native Android APIs, and written in a strict functional programming style.

Do not generate boilerplate object-oriented patterns (no `Manager`, `Helper`, or `Service` classes for business logic). Follow the strict State-Action-Model (SAM) architecture and Functional Kotlin guidelines detailed below.

---

## 1. Core Architecture: State-Action-Model (SAM)
All features must be governed by the SAM pattern using a strict unidirectional data flow. Logic is separated into pure Calculation and impure Execution.

*   **State (Model):** A single, immutable `data class` representing the complete state of the UI or domain (e.g., `GatekeeperState`).
*   **Actions (Messages):** A `sealed interface` defining a closed, finite set of user intents or system events (e.g., `Action.UnlockRequested`).
*   **The Reducer (Pure Calculation):** A pure top-level function that takes the current `State` and an `Action`, returning the *new* `State`.
    *   *Rule:* Reducers must have **ZERO side effects**. No database calls, no network requests, no shared preferences.
*   **The ViewModel/Actor (Impure Execution):** A Native Android `ViewModel` (or other lifecycle-aware component) that hosts the `StateFlow`. It receives `Actions`, passes them to the pure `reduce` function, and executes required side effects (like database writes).
    *   **Dependencies:** This layer receives its dependencies (e.g., API clients, database instances) via **constructor injection**, managed by our `kotlin-inject` component.

---

## 2. Functional Kotlin Rules (Strict Immutability)
Code must be declarative. Do not mutate state.

*   **Immutability by Default:**
    *   Always use `val`. Never use `var` unless contained within a pure function's highly localized scope for extreme performance reasons.
    *   Always use immutable collections (`List`, `Set`, `Map`). Never use `ArrayList` or `MutableList` in public APIs or State.
*   **Transform, Don't Mutate:** To update state, use the `.copy()` method on data classes to create a new instance.
*   **Everything is an Expression:** Utilize Kotlin's expression syntax. Return values directly from `if`, `when`, and `try/catch` blocks. Avoid temporary variables.
    *   *Good:* `val result = if (condition) A else B`
    *   *Bad:* `var result = null; if (condition) { result = A }`
*   **Exhaustive `when` Statements:** Always use exhaustive `when` blocks for evaluating Sealed Interfaces so the compiler guarantees all actions/states are handled.

---

## 3. Functions & Logic
Business logic should not be wrapped in stateful classes.

*   **No Managers or Services:** Avoid OOP anti-patterns like `AuthManager` or `ValidatorService`. We use classes for *capabilities* (like an API client) but not for holding mutable state or business logic.
*   **Top-Level Functions:** Place general domain logic in pure top-level functions (e.g., `validateIntent(intent: Intent): Boolean`).
*   **Extension Functions:** Use extension functions heavily to add behavior to data types without modifying them. This keeps code readable and chained (e.g., `fun VaultItem.toDisplayString(): String`).
*   **Higher-Order Functions:** Prefer functional collection operators (`.map`, `.filter`, `.fold`, `.flatMap`) over imperative `for` loops.

---

## 4. Error Handling: Railway Oriented Programming
Do not use Exceptions for control flow.

*   **No `try/catch` in Business Logic:** Exceptions should only be caught at the absolute boundary of the app (e.g., direct API calls or DB interactions).
*   **Use `Either` or `Result`:** Wrap expected failures in `arrow.core.Either` or Kotlin's native `Result<T>`.
*   Functions that can fail should return an `Either<ErrorType, SuccessType>`. The UI/ViewModel layer will `fold` this result to handle both cases.

---

## 5. Technology Stack & Hard Constraints
*   **UI:** Strict Jetpack Compose. No XML.
*   **Zero Web-Jank:** NEVER suggest or use web wrappers (WebView, Capacitor, React Native) for core UI. The Interceptor must run natively to prevent RAM spikes and OS deprioritization. Headless WebViews are ONLY permitted for the Audio Engine.
*   **Persistence:** Use **SQLDelight** ONLY. Do not use Room, SQLiteOpenHelper, or SharedPreferences for core data. All schemas and queries must be written in raw `.sq` files to guarantee compile-time safety and zero-reflection.
    *   **Migrations (Dev vs Prod):** We use environment-aware schema evolution in `DatabaseDriverFactory`.
        *   **In DEBUG mode (`BuildConfig.DEBUG == true`):** If a migration fails (schema mismatch), the database is permitted to destructively recreate tables (wipe data) to allow rapid iteration.
        *   **In RELEASE mode (`BuildConfig.DEBUG == false`):** Destructive fallbacks are STRICTLY FORBIDDEN. Migration failures must throw exceptions to trigger crash reporting. Production schema changes must use `.sqm` files.
*   **Concurrency:** Strict Structured Concurrency using Kotlin Coroutines and `Flow`/`StateFlow`. Ensure all long-running background tasks are tied to specific `CoroutineScopes` to prevent memory leaks in the Interceptor.
*   **Dependency Injection:** Use **`kotlin-inject`** for compile-time, type-safe dependency injection. Avoid manual dependency wiring and reflection-based frameworks.
*   **Hardware Sensors:** Code interfacing with hardware (e.g., Gyroscope for the Friction Engine) must talk directly to Android native APIs.
*   **Next-Action Predicates (NAPs):** Use Compose `LaunchedEffect` to observe state and trigger automatic side-effects (e.g., automatically unlocking an app when `State.frictionProgress == 1.0`).

---

## 6. Dependency Injection (DI) with `kotlin-inject`
We use `kotlin-inject` for performant, compile-time DI. Follow these patterns:

*   **`@Component`:** The DI graph is defined in an `abstract class` annotated with `@Component`. We have platform-specific components like `AndroidApplicationComponent` that inherit from a `SharedApplicationComponent`.
*   **Constructor Injection:** This is the default. Any class that needs dependencies should declare them in its primary constructor and be annotated with **`@Inject`**.
    ```kotlin
    @Inject class MyRepository(private val database: GatekeeperDatabase) {
        // ...
    }
    ```
*   **`@Provides`:** Use `@Provides` functions inside a Component for dependencies that you don't own, such as library classes (`HttpClient`) or instances requiring the Android `Context`.
    ```kotlin
    @Provides @Singleton
    fun httpClient(): HttpClient = HttpClient(OkHttp) { /* ... */ }
    ```
*   **Scoping:** Use the **`@Singleton`** annotation (or other custom scopes defined in the component) on `@Provides` functions or on `@Inject` classes to manage their lifecycle.
*   **Global Access:** The platform-specific component is initialized and assigned to `GlobalDI.component` at app startup. This allows shared code to access cross-platform dependencies like the `SyncClient`.

---

## 7. Logging Standards: Grug-Brained Visibility
To ensure debuggability across all layers of the system (UI, background services, state logic), all log entries MUST use a single, consistent tag and be prefixed with an emoji to denote the log's category. This allows for rapid visual parsing in Logcat.

*   **Log Tag:** Always use `"Gatekeeper"`.
*   **Log Level:** Use `Log.d` for state changes and routine events. Use `Log.i` for major lifecycle events (service started). Use `Log.w` for recoverable errors. Use `Log.e` for fatal crashes or unrecoverable states.
*   **Visibility:** Logs are automatically mirrored to the host terminal during both `test-unit` (via shadow Log class) and `test-ui` (via auto-piped Logcat in the Nix alias).

### Emoji Legend
*   `📥` **Action Dispatched:** An action was sent to the `GatekeeperStateManager`.
*   `🔄` **State Updated:** The `reduce` function produced a new state different from the previous one.
*   `⚙️` **Side-Effect Triggered:** An impure action (e.g., scheduling a delayed task) was initiated in response to a state change.
*   `🗄️` **Database I/O:** A read from or write to the SQLDelight database occurred.
*   `📡` **Network Call:** An HTTP request was made (e.g., to the YouTube API).
*   `📺` **UI Event:** A significant UI change happened (e.g., overlay shown/removed, screen navigation).
*   `✅` **Success / Lifecycle:** A process completed successfully or a major component (like a service) was connected/started.
*   `❌` **Failure / Error:** An error was caught, a process failed, or a service was unexpectedly destroyed.
*   `👁️` **Observation:** A background service is actively observing system state (e.g., a heartbeat tick, an accessibility event).

---

## Example: The SAM Loop with DI

```kotlin
// 1. State
data class FrictionState(
    val isLocked: Boolean = true,
    val balanceProgress: Float = 0f,
    val bypassReason: String = ""
)

// 2. Actions (Messages)
sealed interface FrictionAction {
    data class GyroUpdated(val tiltX: Float, val tiltY: Float) : FrictionAction
    data class BypassTyped(val text: String) : FrictionAction
    object BypassConfirmed : FrictionAction
}

// 3. Pure Reducer
fun reduce(state: FrictionState, action: FrictionAction): FrictionState = when (action) {
    is FrictionAction.GyroUpdated -> state.copy(
        balanceProgress = calculateNewProgress(state.balanceProgress, action.tiltX, action.tiltY)
    )
    is FrictionAction.BypassTyped -> state.copy(bypassReason = action.text)
    FrictionAction.BypassConfirmed -> state.copy(isLocked = false)
}

// 4. ViewModel (Execution) with DI
@Inject
class FrictionViewModel(
    private val database: GatekeeperDatabase // Injected by kotlin-inject
) : ViewModel() {
    private val _state = MutableStateFlow(FrictionState())
    val state = _state.asStateFlow()

    fun dispatch(action: FrictionAction) {
        _state.value = reduce(_state.value, action)

        // Handle side effects based on new state
        if (action is FrictionAction.BypassConfirmed) {
            viewModelScope.launch {
                database.emergencyBypassLogQueries.insert(
                    /* ... reason = _state.value.bypassReason ... */
                )
            }
        }
    }
}
