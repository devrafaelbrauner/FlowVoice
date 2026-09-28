package dev.rafaelbrauner.flowvoice

import dev.rafaelbrauner.flowvoice.localasr.ARCHIVE_PART_SUFFIX
import dev.rafaelbrauner.flowvoice.localasr.EXTRACTING_SUFFIX
import dev.rafaelbrauner.flowvoice.localasr.REPLACED_SUFFIX
import dev.rafaelbrauner.flowvoice.shared.localasr.NemotronModel
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

    // O modelo do motor no aparelho (~680 MB) e as sobras da instalação não vão para backup nem
    // para outro aparelho: são baixados de novo.
    @Test
    fun localModelFilesAreExcludedFromEveryBackup() {
        assertExcluded(xml("data_extraction_rules.xml"), "cloud-backup", "file", MODEL_FILES)
        assertExcluded(xml("data_extraction_rules.xml"), "device-transfer", "file", MODEL_FILES)
        assertExcluded(xml("backup_rules.xml"), "full-backup-content", "file", MODEL_FILES)
    }

    private fun assertExcluded(
        document: Element,
        section: String,
        domain: String = "sharedpref",
        expected: Set<String> = SENSITIVE_PREFERENCES
    ) {
        val container = if (document.tagName == section) {
            document
        } else {
            document.getElementsByTagName(section).item(0) as Element
        }
        val excludes = container.getElementsByTagName("exclude")
        val excluded = (0 until excludes.length)
            .map { excludes.item(it) as Element }
            .filter { it.getAttribute("domain") == domain }
            .map { it.getAttribute("path") }
            .toSet()
        val missing = expected - excluded
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
        val MODEL_FILES = listOf("", ARCHIVE_PART_SUFFIX, EXTRACTING_SUFFIX, REPLACED_SUFFIX)
            .map { NemotronModel.DIR_NAME + it }
            .toSet()
    }
}
