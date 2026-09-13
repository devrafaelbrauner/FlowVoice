package dev.rafaelbrauner.flowvoice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import dev.rafaelbrauner.flowvoice.service.FlowVoiceAccessibilityService

class FlowVoiceAdbReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getStringExtra("fv_poc_debug_source") != "adb") return
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return

        val service = FlowVoiceAccessibilityService.service
            ?: run {
                Log.i(TAG, "[POC_ADB] serviço não está ativo")
                return
            }

        val action = intent.getStringExtra("fv_poc_action") ?: "diagnose"
        val text = intent.getStringExtra("fv_poc_text") ?: "POC FlowVoice — "
        val result = when (action) {
            "direct" -> service.insertDirect(text)
            "fallback" -> service.insertFallback(text)
            "diagnose" -> service.diagnoseFocusedField()
            else -> FlowVoiceAccessibilityService.InsertResult(false, "debug", "ação desconhecida: $action")
        }
        Log.i(TAG, "[POC_ADB] action=$action resultado=${result.summary}")
    }

    companion object {
        private const val TAG = "FlowVoiceA11y"
    }
}