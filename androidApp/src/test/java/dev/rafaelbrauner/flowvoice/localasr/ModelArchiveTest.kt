package dev.rafaelbrauner.flowvoice.localasr

import dev.rafaelbrauner.flowvoice.shared.localasr.ModelFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream

class ModelArchiveTest {
    private val dir: File = Files.createTempDirectory("model-archive").toFile()
    private val destination = File(dir, "modelo")
    private val temporary = File(dir, "modelo$EXTRACTING_SUFFIX")

    private val encoder = ByteArray(3000) { (it % 97).toByte() }
    private val tokens = "a 0\nb 1\n".toByteArray()
    private val files = listOf(
        ModelFile("encoder.int8.onnx", encoder.size.toLong()),
        ModelFile("tokens.txt", tokens.size.toLong())
    )

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun extractsFilesAtArchiveRoot() = runTest {
        extract(tarBz2(Entry("encoder.int8.onnx", encoder), Entry("tokens.txt", tokens)))

        assertInstalled()
    }

    @Test
    fun extractsPublishedLayoutAndIgnoresExtraFiles() = runTest {
        var lastProgress = 0f
        ModelArchive.extract(archive(published()), destination, files) { lastProgress = it }

        assertInstalled()
        assertEquals(setOf("encoder.int8.onnx", "tokens.txt"), destination.list()!!.toSet())
        assertEquals(1f, lastProgress)
    }

    @Test
    fun ignoresExpectedNameNestedDeeperThanFirstLevel() = runTest {
        val tar = tarBz2(
            Entry("pacote/encoder.int8.onnx", encoder),
            Entry("pacote/extra/tokens.txt", ByteArray(1)),
            Entry("pacote/tokens.txt", tokens)
        )

        extract(tar)

        assertInstalled()
    }

    @Test
    fun rejectsPathTraversal() = runTest {
        assertInvalid(tarBz2(Entry("../tokens.txt", tokens), Entry("encoder.int8.onnx", encoder)))
        assertFalse(File(dir, "tokens.txt").exists())
    }

    @Test
    fun rejectsAbsoluteEntry() = runTest {
        assertInvalid(tarBz2(Entry("/tmp/tokens.txt", tokens), Entry("encoder.int8.onnx", encoder)))
    }

    @Test
    fun safeEntryPathRejectsUnsafeNames() {
        listOf("", "/etc/passwd", "a/../../b", "..", "a\\b", "a\u0000b", "./").forEach { name ->
            assertFailsWith<ModelInstallException>(name) { ModelArchive.safeEntryPath(name) }
        }
        assertEquals(listOf("pacote", "tokens.txt"), ModelArchive.safeEntryPath("./pacote//tokens.txt"))
    }

    @Test
    fun rejectsSymlinkWithExpectedName() = runTest {
        val out = ByteArrayOutputStream()
        TarArchiveOutputStream(BZip2CompressorOutputStream(out)).use { tar ->
            tar.putArchiveEntry(
                TarArchiveEntry("tokens.txt", TarArchiveEntry.LF_SYMLINK).apply { linkName = "/data/segredo" }
            )
            tar.closeArchiveEntry()
            tar.putEntry(Entry("encoder.int8.onnx", encoder))
        }

        assertInvalid(out.toByteArray())
    }

    @Test
    fun rejectsDirectoryWithExpectedName() = runTest {
        val out = ByteArrayOutputStream()
        TarArchiveOutputStream(BZip2CompressorOutputStream(out)).use { tar ->
            tar.putArchiveEntry(TarArchiveEntry("tokens.txt/"))
            tar.closeArchiveEntry()
            tar.putEntry(Entry("encoder.int8.onnx", encoder))
        }

        assertInvalid(out.toByteArray())
    }

    @Test
    fun rejectsWrongDeclaredSize() = runTest {
        assertInvalid(tarBz2(Entry("encoder.int8.onnx", encoder + 1), Entry("tokens.txt", tokens)))
        assertInvalid(tarBz2(Entry("encoder.int8.onnx", encoder.copyOf(10)), Entry("tokens.txt", tokens)))
    }

    @Test
    fun rejectsTruncatedArchive() = runTest {
        val whole = tarBz2(Entry("encoder.int8.onnx", encoder), Entry("tokens.txt", tokens))

        assertInvalid(whole.copyOf(whole.size / 2))
    }

    @Test
    fun rejectsMissingFile() = runTest {
        assertInvalid(tarBz2(Entry("encoder.int8.onnx", encoder)))
    }

    @Test
    fun rejectsDuplicateFile() = runTest {
        assertInvalid(
            tarBz2(
                Entry("encoder.int8.onnx", encoder),
                Entry("tokens.txt", tokens),
                Entry("pacote/tokens.txt", tokens)
            )
        )
    }

    @Test
    fun rejectsNonArchive() = runTest {
        assertInvalid("não é um tar.bz2".toByteArray())
    }

    @Test
    fun keepsInstalledModelWhenNewArchiveFails() = runTest {
        destination.mkdirs()
        File(destination, "tokens.txt").writeText("antigo")

        assertInvalid(tarBz2(Entry("tokens.txt", tokens)))

        assertEquals("antigo", File(destination, "tokens.txt").readText())
    }

    @Test
    fun replacesInstalledModelWhenNewArchiveIsValid() = runTest {
        destination.mkdirs()
        File(destination, "tokens.txt").writeText("antigo")

        extract(published())

        assertInstalled()
        assertFalse(File(dir, "modelo$REPLACED_SUFFIX").exists())
    }

    @Test
    fun cancellationRemovesTemporaryDirectory() = runTest {
        assertFailsWith<CancellationException> {
            ModelArchive.extract(archive(published()), destination, files) {
                throw CancellationException("cancelado")
            }
        }

        assertFalse(temporary.exists())
        assertFalse(destination.exists())
    }

    @Test
    fun receiveReturnsSha256OfWrittenBytes() = runTest {
        val bytes = "abc".toByteArray()
        val target = File(dir, "m.part")
        val progress = mutableListOf<Long>()

        val sha = ModelArchive.receive(ByteArrayInputStream(bytes), target, bytes.size.toLong()) { progress += it }

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha)
        assertContentEquals(bytes, target.readBytes())
        assertEquals(3L, progress.last())
    }

    @Test
    fun receiveRejectsOversizedDownloadBeforeWritingPastTheLimit() = runTest {
        val target = File(dir, "m.part")

        assertFailsWith<ModelInstallException> {
            ModelArchive.receive(ByteArrayInputStream(ByteArray(100)), target, 10) {}
        }
        assertTrue(target.length() <= 10)
    }

    @Test
    fun receiveRejectsShortDownload() = runTest {
        assertFailsWith<ModelInstallException> {
            ModelArchive.receive(ByteArrayInputStream(ByteArray(9)), File(dir, "m.part"), 10) {}
        }
    }

    @Test
    fun sha256MatchesKnownVector() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ModelArchive.sha256(ByteArray(0))
        )
    }

    @Test
    fun diskSpaceIsEnoughOnlyFromTheRequiredAmount() {
        assertTrue(ModelDiskSpace.enough(usableBytes = 1000, reclaimableBytes = 0, requiredBytes = 1000))
        assertFalse(ModelDiskSpace.enough(usableBytes = 999, reclaimableBytes = 0, requiredBytes = 1000))
        assertTrue(ModelDiskSpace.enough(usableBytes = 600, reclaimableBytes = 400, requiredBytes = 1000))
    }

    @Test
    fun sizesUseDecimalUnitsWithBrazilianComma() {
        assertEquals("475 MB", ModelDiskSpace.megabytes(475_271_763))
        assertEquals("1,2 GB", ModelDiskSpace.gigabytes(1_224_595_983))
    }

    private suspend fun extract(tar: ByteArray) {
        ModelArchive.extract(archive(tar), destination, files) {}
    }

    private suspend fun assertInvalid(tar: ByteArray) {
        val error = assertFailsWith<ModelInstallException> { extract(tar) }
        assertEquals(INVALID_ARCHIVE, error.message)
        assertFalse(temporary.exists(), "pasta temporária ficou no disco")
    }

    private fun assertInstalled() {
        assertContentEquals(encoder, File(destination, "encoder.int8.onnx").readBytes())
        assertContentEquals(tokens, File(destination, "tokens.txt").readBytes())
        assertFalse(temporary.exists())
    }

    // O pacote como o sherpa-onnx publica: tudo numa pasta, com sobras.
    private fun published() = tarBz2(
        Entry("sherpa-onnx-modelo/README.md", "leia".toByteArray()),
        Entry("sherpa-onnx-modelo/encoder.int8.onnx", encoder),
        Entry("sherpa-onnx-modelo/test_wavs/0.wav", ByteArray(10)),
        Entry("sherpa-onnx-modelo/tokens.txt", tokens)
    )

    private fun archive(bytes: ByteArray) = File(dir, "m.tar.bz2").apply { writeBytes(bytes) }

    private class Entry(val name: String, val bytes: ByteArray)

    private fun tarBz2(vararg entries: Entry): ByteArray {
        val out = ByteArrayOutputStream()
        TarArchiveOutputStream(BZip2CompressorOutputStream(out)).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            entries.forEach { tar.putEntry(it) }
        }
        return out.toByteArray()
    }

    private fun TarArchiveOutputStream.putEntry(entry: Entry) {
        // `true`: sem isso o commons-compress tira a barra de "/tmp/x" ao gravar.
        putArchiveEntry(TarArchiveEntry(entry.name, true).apply { size = entry.bytes.size.toLong() })
        write(entry.bytes)
        closeArchiveEntry()
    }
}
