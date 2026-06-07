package com.bornomala.keyboard.ime

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Minimal owner that supplies the Lifecycle / ViewModelStore / SavedStateRegistry a
 * Compose `ComposeView` needs in order to compose. An [android.inputmethodservice.InputMethodService]
 * is not a [LifecycleOwner], so its input view is hosted in a window without these owners;
 * attaching this host via the `setViewTree*Owner` extensions makes Compose (and
 * `collectAsStateWithLifecycle`) work inside the IME.
 *
 * The service drives the lifecycle: [onCreate] when the input view is built, [onResume] when
 * shown, [onPause] when hidden, [onDestroy] when the service is torn down.
 */
class ImeComposeHost :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onPause() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
