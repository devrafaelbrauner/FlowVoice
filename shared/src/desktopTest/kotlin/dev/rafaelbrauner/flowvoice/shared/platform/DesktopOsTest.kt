package dev.rafaelbrauner.flowvoice.shared.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopOsTest {
    @Test
    fun detectsWindowsVariants() {
        assertEquals(DesktopOs.Windows, DesktopOs.fromOsName("Windows 11"))
        assertEquals(DesktopOs.Windows, DesktopOs.fromOsName("Windows Server 2022"))
    }

    @Test
    fun detectsMacAndLinux() {
        assertEquals(DesktopOs.MacOs, DesktopOs.fromOsName("Mac OS X"))
        assertEquals(DesktopOs.Linux, DesktopOs.fromOsName(" Linux "))
    }

    @Test
    fun unknownOrMissingIsOther() {
        assertEquals(DesktopOs.Other, DesktopOs.fromOsName("FreeBSD"))
        assertEquals(DesktopOs.Other, DesktopOs.fromOsName(null))
        assertEquals(DesktopOs.Other, DesktopOs.fromOsName(""))
    }
}
