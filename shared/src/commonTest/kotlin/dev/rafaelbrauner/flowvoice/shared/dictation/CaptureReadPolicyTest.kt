package dev.rafaelbrauner.flowvoice.shared.dictation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// P154: a leitura da captura devolveu `code=0` e o ditado morreu no meio. Zero byte não é fim de
// fluxo — é buffer vazio. Aqui fica a decisão pura: tolerar, reabrir uma vez, ou desistir.
class CaptureReadPolicyTest {
    @Test
    fun emptyReadIsTransientAndKeepsCapturing() {
        val policy = CaptureReadPolicy()

        val action = policy.onRead(bytesRead = 0, captureActive = true, nowMs = 0L)

        assertIs<CaptureReadAction.WaitAndRetry>(action)
    }

    @Test
    fun severalEmptyReadsInsideTheToleranceKeepCapturing() {
        val policy = CaptureReadPolicy(emptyToleranceMs = 300L)

        assertIs<CaptureReadAction.WaitAndRetry>(policy.onRead(0, captureActive = true, nowMs = 0L))
        assertIs<CaptureReadAction.WaitAndRetry>(policy.onRead(0, captureActive = true, nowMs = 100L))
        assertIs<CaptureReadAction.WaitAndRetry>(policy.onRead(0, captureActive = true, nowMs = 299L))
        assertEquals(3, policy.consecutiveEmptyReads)
    }

    // Áudio que volta a chegar apaga o histórico: a próxima sequência de vazios recomeça a contagem
    // e ganha a tolerância inteira de novo.
    @Test
    fun audioAfterEmptyReadsResetsTheTolerance() {
        val policy = CaptureReadPolicy(emptyToleranceMs = 300L)
        policy.onRead(0, captureActive = true, nowMs = 0L)
        policy.onRead(0, captureActive = true, nowMs = 100L)

        assertIs<CaptureReadAction.Deliver>(policy.onRead(3_200, captureActive = true, nowMs = 200L))
        assertEquals(0, policy.consecutiveEmptyReads)

        assertIs<CaptureReadAction.WaitAndRetry>(policy.onRead(0, captureActive = true, nowMs = 300L))
        assertIs<CaptureReadAction.WaitAndRetry>(policy.onRead(0, captureActive = true, nowMs = 599L))
    }

    @Test
    fun emptyReadsBeyondTheToleranceAskForOneRestart() {
        val policy = CaptureReadPolicy(emptyToleranceMs = 300L)
        policy.onRead(0, captureActive = true, nowMs = 0L)

        assertIs<CaptureReadAction.Restart>(policy.onRead(0, captureActive = true, nowMs = 300L))
    }

    // Reabrir é a última tentativa: se depois dela o microfone continuar mudo, a sessão termina —
    // mas com motivo no log, não com um `code=0` solto.
    @Test
    fun emptyReadsAfterTheRestartGiveUp() {
        val policy = CaptureReadPolicy(emptyToleranceMs = 300L)
        policy.onRead(0, captureActive = true, nowMs = 0L)
        assertIs<CaptureReadAction.Restart>(policy.onRead(0, captureActive = true, nowMs = 300L))

        assertIs<CaptureReadAction.WaitAndRetry>(policy.onRead(0, captureActive = true, nowMs = 310L))
        val action = policy.onRead(0, captureActive = true, nowMs = 700L)

        val giveUp = assertIs<CaptureReadAction.GiveUp>(action)
        assertTrue(giveUp.reason.isNotBlank(), "desistir sem motivo não ajuda a próxima medição")
    }

    // O `AudioRecord.stop()` destrava a leitura pendente, que volta com zero. Isso é o encerramento
    // normal, não falha: não pode virar erro nem log de pânico.
    @Test
    fun emptyReadWhileStoppingIsExpected() {
        val policy = CaptureReadPolicy()

        assertIs<CaptureReadAction.Stop>(policy.onRead(0, captureActive = false, nowMs = 0L))
    }

    @Test
    fun deadObjectRestartsImmediately() {
        val policy = CaptureReadPolicy()

        assertIs<CaptureReadAction.Restart>(
            policy.onRead(CaptureReadPolicy.ERROR_DEAD_OBJECT, captureActive = true, nowMs = 0L)
        )
    }

    @Test
    fun deadObjectTwiceGivesUp() {
        val policy = CaptureReadPolicy()
        policy.onRead(CaptureReadPolicy.ERROR_DEAD_OBJECT, captureActive = true, nowMs = 0L)

        assertIs<CaptureReadAction.GiveUp>(
            policy.onRead(CaptureReadPolicy.ERROR_DEAD_OBJECT, captureActive = true, nowMs = 10L)
        )
    }

    // Parâmetro inválido não melhora tentando de novo.
    @Test
    fun otherNegativeCodesGiveUpAtOnce() {
        val policy = CaptureReadPolicy()

        val giveUp = assertIs<CaptureReadAction.GiveUp>(
            policy.onRead(CaptureReadPolicy.ERROR_BAD_VALUE, captureActive = true, nowMs = 0L)
        )
        assertTrue(giveUp.reason.contains("-2"), "o motivo precisa carregar o código: ${giveUp.reason}")
    }

    @Test
    fun restartResetsTheToleranceWindow() {
        val policy = CaptureReadPolicy(emptyToleranceMs = 300L)
        policy.onRead(0, captureActive = true, nowMs = 0L)
        assertIs<CaptureReadAction.Restart>(policy.onRead(0, captureActive = true, nowMs = 300L))

        // Depois de reabrir, o relógio da tolerância recomeça: 299 ms de vazios ainda são toleráveis.
        assertIs<CaptureReadAction.WaitAndRetry>(policy.onRead(0, captureActive = true, nowMs = 400L))
        assertIs<CaptureReadAction.WaitAndRetry>(policy.onRead(0, captureActive = true, nowMs = 598L))
    }
}
