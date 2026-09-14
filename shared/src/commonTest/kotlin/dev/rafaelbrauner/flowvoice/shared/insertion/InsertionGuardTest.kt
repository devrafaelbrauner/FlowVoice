package dev.rafaelbrauner.flowvoice.shared.insertion

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InsertionGuardTest {
    @Test
    fun textPasswordVariationsAreDetected() {
        assertTrue(InsertionGuard.isPasswordInputType(TEXT or 0x80))
        assertTrue(InsertionGuard.isPasswordInputType(TEXT or 0x90))
        assertTrue(InsertionGuard.isPasswordInputType(TEXT or 0xe0))
    }

    @Test
    fun numberPasswordIsDetected() {
        assertTrue(InsertionGuard.isPasswordInputType(NUMBER or 0x10))
    }

    @Test
    fun ordinaryFieldsAreNotPasswords() {
        assertFalse(InsertionGuard.isPasswordInputType(TEXT))
        assertFalse(InsertionGuard.isPasswordInputType(TEXT or 0x20))
        assertFalse(InsertionGuard.isPasswordInputType(NUMBER))
        assertFalse(InsertionGuard.isPasswordInputType(0))
    }

    @Test
    fun numberVariationBitsOnTextClassAreNotPassword() {
        assertFalse(InsertionGuard.isPasswordInputType(TEXT or 0x10))
    }

    @Test
    fun destinationChangeBlocksOnlyWhenBothPackagesAreKnownAndDiffer() {
        assertTrue(InsertionGuard.destinationChanged("com.whatsapp", "com.android.chrome"))
        assertFalse(InsertionGuard.destinationChanged("com.whatsapp", "com.whatsapp"))
        assertFalse(InsertionGuard.destinationChanged(null, "com.android.chrome"))
        assertFalse(InsertionGuard.destinationChanged("com.whatsapp", null))
    }

    private companion object {
        const val TEXT = 0x1
        const val NUMBER = 0x2
    }
}
