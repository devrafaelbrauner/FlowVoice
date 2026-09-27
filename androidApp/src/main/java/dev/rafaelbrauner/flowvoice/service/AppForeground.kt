package dev.rafaelbrauner.flowvoice.service

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Activities do FlowVoice na tela (entre onStart e onStop). Girar o aparelho recria a activity: a que
// para já sabe que volta, então a troca não conta como saída e a bolha não pisca (R5d).
class StartedActivities {
    private var started = 0
    private var recreating = 0

    val any: Boolean
        get() = started > 0

    fun onStarted() {
        if (recreating > 0) recreating-- else started++
    }

    fun onStopped(changingConfigurations: Boolean) {
        if (changingConfigurations) {
            recreating++
        } else if (started > 0) {
            started--
        }
    }
}

object AppForeground : Application.ActivityLifecycleCallbacks {
    private val activities = StartedActivities()
    private val state = MutableStateFlow(false)

    /** Verdadeiro enquanto alguma tela do FlowVoice está visível; a bolha ociosa some nesse intervalo. */
    val inForeground: StateFlow<Boolean> = state.asStateFlow()

    override fun onActivityStarted(activity: Activity) {
        activities.onStarted()
        state.value = activities.any
    }

    override fun onActivityStopped(activity: Activity) {
        activities.onStopped(activity.isChangingConfigurations)
        state.value = activities.any
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
