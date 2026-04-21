package com.aegisgatekeeper.app

// ... keep all the existing android/compose/lifecycle imports ...
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aegisgatekeeper.app.domain.ContentItem
import com.aegisgatekeeper.app.domain.ContentType
import com.aegisgatekeeper.app.domain.GatekeeperAction
import com.aegisgatekeeper.app.domain.GatekeeperTheme
import com.aegisgatekeeper.app.domain.IndustrialButton
import com.aegisgatekeeper.app.domain.IndustrialTextField
import com.aegisgatekeeper.app.views.BallBalancingUi
import com.aegisgatekeeper.app.views.interception.InterceptionScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private var compositionScope: CoroutineScope? = null

    // show() and remove() methods remain IDENTICAL to before...
    fun show(context: Context) {
        if (composeView != null) return

        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params =
                WindowManager
                    .LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT,
                    ).apply {
                        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                    }

            val owner = OverlayLifecycleOwner()
            owner.onCreate()
            lifecycleOwner = owner

            val recomposer = Recomposer(AndroidUiDispatcher.CurrentThread)
            compositionScope = CoroutineScope(AndroidUiDispatcher.CurrentThread + SupervisorJob())
            compositionScope?.launch { recomposer.runRecomposeAndApplyChanges() }

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
                            GatekeeperTheme {
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
            composeView?.let {
                it.disposeComposition()
                windowManager.removeView(it)
                Log.d("Gatekeeper", "📺 GatekeeperOverlay: OVERLAY REMOVED from WindowManager")
            }
        } catch (e: Exception) {
            Log.w("Gatekeeper", "❌ GatekeeperOverlay: Overlay removal warning: ${e.message}")
        } finally {
            compositionScope?.cancel()
            compositionScope = null
            lifecycleOwner?.onDestroy()
            composeView = null
            lifecycleOwner = null
        }
    }
}
