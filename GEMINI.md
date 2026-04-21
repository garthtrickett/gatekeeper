# Gemini Customization File

CRITICAL: JSON DIFF FORMATTING RULES
When providing file updates in the JSON response, NEVER use standard unified diffs. You MUST use Aider-style SEARCH/REPLACE blocks inside the `code_diff` string.

1. The root of your response MUST be a SINGLE JSON object. NEVER return a JSON array at the root level.
2. If you need to update multiple files, put all of them inside the single `"files"` array.
3. Every change must be formatted exactly like this:

{
  "summary": "Example summary of all changes.",
  "files":[
    {
      "file_path": "src/lib/shared/example-file.ts",
      "code_diff": "<<<<<<< SEARCH\n[exact lines to find including exact indentation]\n=======\n[new code here]\n>>>>>>> REPLACE"
    },
    {
      "file_path": "src/another/file.ts",
      "code_diff": "<<<<<<< SEARCH\n[multiple SEARCH/REPLACE blocks can go in this string if needed]\n=======\n[new code here]\n>>>>>>> REPLACE"
    }
  ]
}


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
*   **The ViewModel/Actor (Impure Execution):** A Native Android `ViewModel` that hosts the `StateFlow`. It receives `Actions`, passes them to the pure `reduce` function, and executes required side effects (like database writes) based on the resulting state using Coroutines.

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

*   **No Managers or Services:** Avoid OOP anti-patterns like `AuthManager` or `ValidatorService`. 
*   **Top-Level Functions:** Place general domain logic in pure top-level functions (e.g., `validateIntent(intent: Intent): Boolean`).
*   **Extension Functions:** Use extension functions heavily to add behavior to data types without modifying them. This keeps code readable and chained (e.g., `fun VaultItem.toDisplayString(): String`).
*   **Higher-Order Functions:** Prefer functional collection operators (`.map`, `.filter`, `.fold`, `.flatMap`) over imperative `for` loops.

---

## 4. Error Handling: Railway Oriented Programming
Do not use Exceptions for control flow.

*   **No `try/catch` in Business Logic:** Exceptions should only be caught at the absolute boundary of the app (e.g., direct API calls or DB interactions).
*   **Use `Result` Types:** Wrap expected failures in Kotlin's native `Result<T>` or a custom `sealed class` (e.g., `sealed interface DomainError`).
*   Functions that can fail should return `Result<SuccessType, ErrorType>`. The UI/ViewModel layer will fold or unwrap this result.

---

## 5. Technology Stack & Hard Constraints
*   **UI:** Strict Jetpack Compose. No XML. 
*   **Zero Web-Jank:** NEVER suggest or use web wrappers (WebView, Capacitor, React Native) for core UI. The Interceptor must run natively to prevent RAM spikes and OS deprioritization. Headless WebViews are ONLY permitted for the Audio Engine.
*   **Persistence:** Use **SQLDelight** ONLY. Do not use Room, SQLiteOpenHelper, or SharedPreferences for core data. All schemas and queries must be written in raw `.sq` files to guarantee compile-time safety and zero-reflection. Do not use LocalStorage.
*   **Concurrency:** Strict Structured Concurrency using Kotlin Coroutines and `Flow`/`StateFlow`. Ensure all long-running background tasks are tied to specific `CoroutineScopes` to prevent memory leaks in the Interceptor.
*   **Hardware Sensors:** Code interfacing with hardware (e.g., Gyroscope for the Friction Engine) must talk directly to Android native APIs.
*   **Next-Action Predicates (NAPs):** Use Compose `LaunchedEffect` to observe state and trigger automatic side-effects (e.g., automatically unlocking an app when `State.frictionProgress == 1.0`).

---

## 6. Logging Standards: Grug-Brained Visibility
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

## Example: The SAM Loop

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

// 4. ViewModel (Execution)
class FrictionViewModel : ViewModel() {
    private val _state = MutableStateFlow(FrictionState())
    val state = _state.asStateFlow()

    fun dispatch(action: FrictionAction) {
        _state.value = reduce(_state.value, action)
        
        // Handle side effects based on new state
        if (action is FrictionAction.BypassConfirmed) {
            viewModelScope.launch {
                database.logBypassReason(_state.value.bypassReason)
            }
        }
    }
}


