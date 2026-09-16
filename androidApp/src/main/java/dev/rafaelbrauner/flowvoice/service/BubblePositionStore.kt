package dev.rafaelbrauner.flowvoice.service

import android.content.Context
import androidx.core.content.edit
import dev.rafaelbrauner.flowvoice.ui.overlay.BubblePosition

// Posição da bolha (P138) fora do AppPreferences, que é sincronizado: lugar na tela é do aparelho.
class BubblePositionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): BubblePosition =
        BubblePosition.decode(prefs.getString(KEY_SIDE, null), prefs.getFloat(KEY_FRACTION, Float.NaN))

    fun write(position: BubblePosition) {
        prefs.edit {
            putString(KEY_SIDE, position.encodedSide)
            putFloat(KEY_FRACTION, position.fraction)
        }
    }

    // "Bolha ligada" sobrevive à morte do processo, que uma reinstalação ou atualização provoca (P141).
    fun readEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun writeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
    }

    private companion object {
        const val PREFS = "flowvoice_overlay"
        const val KEY_SIDE = "bubble_side"
        const val KEY_FRACTION = "bubble_fraction"
        const val KEY_ENABLED = "bubble_enabled"
    }
}
