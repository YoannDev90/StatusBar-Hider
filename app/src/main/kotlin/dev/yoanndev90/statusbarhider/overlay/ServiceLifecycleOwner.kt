package dev.yoanndev90.statusbarhider.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Manual LifecycleOwner for the WindowManager-hosted ComposeView: a window
 * has no Activity to inherit one from, so the overlay service drives the
 * registry through [create] / [start] / [resume] / … as the view attaches.
 */
internal class ServiceLifecycleOwner :
	LifecycleOwner,
	SavedStateRegistryOwner {
	private val registry = LifecycleRegistry(this)
	private val savedStateController by lazy { SavedStateRegistryController.create(this) }

	override val lifecycle: Lifecycle get() = registry
	override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

	fun create() {
		savedStateController.performAttach()
		savedStateController.performRestore(null)
		registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
	}

	fun start() {
		registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
	}

	fun resume() {
		registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
	}

	fun pause() {
		registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
	}

	fun stop() {
		registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
	}

	fun destroy() {
		registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
	}
}
