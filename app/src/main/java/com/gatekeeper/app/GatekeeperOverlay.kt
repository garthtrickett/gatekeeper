package com.gatekeeper.app

// ... keep all the existing android/compose/lifecycle imports ...
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gatekeeper.app.domain.GatekeeperAction
import com.gatekeeper.app.views.BallBalancingUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// OverlayLifecycleOwner class remains IDENTICAL to before...
private class OverlayLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}

object GatekeeperOverlay {
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    // show() and remove() methods remain IDENTICAL to before...
    fun show(context: Context) {
        if (composeView != null) return

        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params =
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                )

            val owner = OverlayLifecycleOwner()
            owner.onCreate()
            lifecycleOwner = owner

            val recomposer = Recomposer(AndroidUiDispatcher.CurrentThread)
            val compositionScope = CoroutineScope(AndroidUiDispatcher.CurrentThread + SupervisorJob())
            compositionScope.launch { recomposer.runRecomposeAndApplyChanges() }

            composeView =
                ComposeView(context).apply {
                    compositionContext = recomposer
                    setViewTreeLifecycleOwner(owner)
                    setViewTreeSavedStateRegistryOwner(owner)
                    setViewTreeViewModelStoreOwner(owner)

                    setContent {
                        CompositionLocalProvider(
                            LocalLifecycleOwner provides owner,
                            LocalContext provides context,
                        ) {
                            MaterialTheme {
                                // NEW: We render the choice screen by default
                                InterceptionScreen()
                            }
                        }
                    }
                }

            windowManager.addView(composeView, params)
            owner.onResume()
            Log.i("Gatekeeper", "✅ OVERLAY ATTACHED SUCCESSFULLY")
        } catch (e: Exception) {
            Log.e("Gatekeeper", "❌ FATAL OVERLAY CRASH: ${e.javaClass.simpleName} - ${e.message}")
            Log.e("Gatekeeper", Log.getStackTraceString(e))
        }
    }

    fun remove(context: Context) {
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            composeView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.w("Gatekeeper", "Overlay removal warning: ${e.message}")
        } finally {
            lifecycleOwner?.onDestroy()
            composeView = null
            lifecycleOwner = null
        }
    }
}

// --- NEW NAVIGATIONAL COMPOSABLE ---
@Suppress("FunctionName")
@Composable
private fun InterceptionScreen() {
    val state by GatekeeperStateManager.state.collectAsState()
    val interceptedPackage = state.currentlyInterceptedApp ?: return

    // This local state determines which screen to show: CHOICE, BYPASS, or FRICTION
    var screen by remember { mutableStateOf("CHOICE") }

    when (screen) {
        "CHOICE" -> {
            InterceptionChoiceUi(
                interceptedPackage = interceptedPackage,
                onBypass = { screen = "BYPASS" },
                onFriction = { screen = "FRICTION" },
            )
        }

        "BYPASS" -> {
            EmergencyBypassUi(interceptedPackage = interceptedPackage)
        }

        "FRICTION" -> {
            BallBalancingUi(interceptedPackage = interceptedPackage)
        }
    }
}

@Suppress("FunctionName")
@Composable
private fun InterceptionChoiceUi(
    interceptedPackage: String,
    onBypass: () -> Unit,
    onFriction: () -> Unit,
) {
    val appName = getAppName(interceptedPackage)
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            // The randomized position "Close" button to break muscle memory
            com.gatekeeper.app.views.MovingCloseButton(onClose = {
                GatekeeperStateManager.dispatch(GatekeeperAction.DismissOverlay)
            })

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Take a breath.",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "You are about to open $appName.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(64.dp))

                // The main "Friction" button
                Button(onClick = onFriction) {
                    Text(text = "Continue with friction", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // The "Emergency" button
                OutlinedButton(onClick = onBypass) {
                    Text(text = "Emergency Bypass")
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // DEBUG: Test SQLDelight Writes
                Button(onClick = {
                    GatekeeperStateManager.dispatch(
                        GatekeeperAction.SaveToVault(
                            query = "Test Vault Query ${System.currentTimeMillis() % 1000}",
                            currentTimestamp = System.currentTimeMillis()
                        )
                    )
                }) {
                    Text(text = "DEBUG: Save to Vault")
                }
            }
        }
    }
}

// EmergencyBypassUi is now simplified and takes the package name as a parameter
@Suppress("FunctionName")
@Composable
private fun EmergencyBypassUi(interceptedPackage: String) {
    var reason by remember { mutableStateOf("") }
    val appName = getAppName(interceptedPackage)

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Emergency Bypass",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Why do you need to open $appName?",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(48.dp))
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("e.g. 'I need an Uber'") },
                modifier = Modifier.width(300.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val trimmedReason = reason.trim()
                    if (trimmedReason.isNotBlank()) {
                        GatekeeperStateManager.dispatch(
                            GatekeeperAction.EmergencyBypassRequested(
                                packageName = interceptedPackage,
                                reason = trimmedReason,
                                currentTimestamp = System.currentTimeMillis(),
                            ),
                        )
                    }
                },
                enabled = reason.trim().isNotBlank(),
            ) {
                Text(text = "Unlock for 5 minutes")
            }
        }
    }
}

// getAppName helper function remains IDENTICAL to before...
@Composable
private fun getAppName(packageName: String): String {
    val context = LocalContext.current
    return try {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}
