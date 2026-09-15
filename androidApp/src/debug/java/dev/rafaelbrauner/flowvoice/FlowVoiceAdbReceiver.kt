package dev.rafaelbrauner.flowvoice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.rafaelbrauner.flowvoice.service.FlowVoiceAccessibilityService
import dev.rafaelbrauner.flowvoice.shared.pipeline.DictationPipeline
import org.koin.core.context.GlobalContext

class FlowVoiceAdbReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getStringExtra("fv_poc_debug_source") != "adb") return

        // Fecha o microfone na hora num teste sem fala no aparelho, antes de a primeira janela ir à OpenRouter.
        if (intent.getStringExtra("fv_poc_action") == "cancel") {
            GlobalContext.get().get<DictationPipeline>().requestCancel()
            Log.i(TAG, "[POC_ADB] action=cancel pedido")
            return
        }

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
