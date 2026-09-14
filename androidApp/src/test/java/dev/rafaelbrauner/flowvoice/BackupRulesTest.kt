package dev.rafaelbrauner.flowvoice

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import org.w3c.dom.Element

class BackupRulesTest {

    @Test
    fun sensitivePreferencesAreExcludedFromCloudBackup() {
        assertExcluded(xml("data_extraction_rules.xml"), "cloud-backup")
    }

    @Test
    fun sensitivePreferencesAreExcludedFromDeviceTransfer() {
        assertExcluded(xml("data_extraction_rules.xml"), "device-transfer")
    }

    @Test
    fun sensitivePreferencesAreExcludedFromFullBackup() {
        assertExcluded(xml("backup_rules.xml"), "full-backup-content")
    }

    private fun assertExcluded(document: Element, section: String) {
        val container = if (document.tagName == section) {
            document
        } else {
            document.getElementsByTagName(section).item(0) as Element
        }
        val excludes = container.getElementsByTagName("exclude")
        val excluded = (0 until excludes.length)
            .map { excludes.item(it) as Element }
            .filter { it.getAttribute("domain") == "sharedpref" }
            .map { it.getAttribute("path") }
            .toSet()
        val missing = SENSITIVE_PREFERENCES - excluded
        assertTrue(missing.isEmpty(), "$section sem exclusão de: $missing")
    }

    private fun xml(name: String): Element =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/res/xml/$name"))
            .documentElement

    private companion object {
        val SENSITIVE_PREFERENCES = setOf(
            "flowvoice_vault.xml",
            "flowvoice_secrets.xml",
            "flowvoice_notes.xml",
            "flowvoice_dictionary.xml",
            "flowvoice_auth.xml"
        )
    }
}
