package dev.rafaelbrauner.flowvoice.service

import android.content.Context
import dev.rafaelbrauner.flowvoice.ui.overlay.BubblePosition

// Posição da bolha (P138) fora do AppPreferences, que é sincronizado: lugar na tela é do aparelho.
class BubblePositionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): BubblePosition =
        BubblePosition.decode(prefs.getString(KEY_SIDE, null), prefs.getFloat(KEY_FRACTION, Float.NaN))

    fun write(position: BubblePosition) {
        prefs.edit()
            .putString(KEY_SIDE, position.encodedSide)
            .putFloat(KEY_FRACTION, position.fraction)
            .apply()
    }

    private companion object {
        const val PREFS = "flowvoice_overlay"
        const val KEY_SIDE = "bubble_side"
        const val KEY_FRACTION = "bubble_fraction"
    }
}
