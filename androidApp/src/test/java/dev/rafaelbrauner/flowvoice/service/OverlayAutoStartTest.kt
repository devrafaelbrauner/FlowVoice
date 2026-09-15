package dev.rafaelbrauner.flowvoice.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlayAutoStartTest {
    private fun decide(
        enabled: Boolean = true,
        running: Boolean = false,
        sdkInt: Int = 36,
        canDrawOverlays: Boolean = true,
        microphoneGranted: Boolean = true
    ) = OverlayAutoStart.shouldRestart(enabled, running, sdkInt, canDrawOverlays, microphoneGranted)

    @Test
    fun bubbleLigadaAntesDaAtualizacaoVoltaQuandoOAppAbre() {
        assertTrue(decide())
    }

    @Test
    fun bolhaDesligadaPeloUsuarioNaoVolta() {
        assertFalse(decide(enabled = false))
    }

    @Test
    fun bolhaJaNaTelaNaoEIniciadaDeNovo() {
        assertFalse(decide(running = true))
    }

    @Test
    fun semPermissaoDeSobreposicaoOuMicrofoneNaoTenta() {
        assertFalse(decide(canDrawOverlays = false))
        assertFalse(decide(microphoneGranted = false))
        assertFalse(decide(sdkInt = 25))
    }
}
