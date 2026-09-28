package dev.rafaelbrauner.flowvoice.localasr

import dev.rafaelbrauner.flowvoice.shared.localasr.ModelFile
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

const val EXTRACTING_SUFFIX = ".extracting"
const val REPLACED_SUFFIX = ".old"
const val ARCHIVE_PART_SUFFIX = ".tar.bz2.part"

internal const val INVALID_ARCHIVE = "Pacote do modelo inválido — tente de novo"
internal const val WRITE_FAILED = "Não foi possível gravar o modelo no aparelho — tente de novo"

// Falha que já traz a frase para o usuário.
class ModelInstallException(message: String) : Exception(message)

object ModelArchive {
    private const val BUFFER = 1 shl 16

    // O nome de cada entrada é texto de quem fez o pacote: um "../shared_prefs/x" não é o pacote
    // publicado, e o resto dele também não é extraído.
    fun safeEntryPath(name: String): List<String> {
        if (name.isEmpty() || name.startsWith("/") || name.contains('\\') || name.contains('\u0000')) {
            throw ModelInstallException(INVALID_ARCHIVE)
        }
        val parts = name.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) throw ModelInstallException(INVALID_ARCHIVE)
        return parts
    }

    // Grava o download em [target] conferindo o tamanho enquanto chega (um servidor que mande mais
    // para na hora, sem encher o disco) e devolve o SHA-256 do que foi gravado.
    suspend fun receive(
        input: InputStream,
        target: File,
        expectedBytes: Long,
        onProgress: (Long) -> Unit
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        var received = 0L
        val output = try {
            target.outputStream()
        } catch (_: IOException) {
            throw ModelInstallException(WRITE_FAILED)
        }
        output.use {
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                received += read
                if (received > expectedBytes) throw ModelInstallException(INVALID_ARCHIVE)
                digest.update(buffer, 0, read)
                try {
                    output.write(buffer, 0, read)
                } catch (_: IOException) {
                    throw ModelInstallException(WRITE_FAILED)
                }
                onProgress(received)
            }
        }
        if (received != expectedBytes) throw ModelInstallException(INVALID_ARCHIVE)
        return digest.digest().toHex()
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    // Tira de [archive] (tar.bz2) só os [files] esperados e os põe em [destination] de uma vez:
    // tudo vai para "<destination>.extracting" e só vira [destination] por rename depois de conferido.
    // Falha ou cancelamento apagam a pasta temporária; um modelo que já estivesse em [destination]
    // só sai na troca final e volta se ela falhar.
    // [onProgress]: fração do arquivo compactado já lida, de 0 a 1.
    suspend fun extract(
        archive: File,
        destination: File,
        files: List<ModelFile>,
        onProgress: (Float) -> Unit
    ) {
        val parent = destination.parentFile ?: throw ModelInstallException(WRITE_FAILED)
        val temporary = File(parent, destination.name + EXTRACTING_SUFFIX)
        temporary.deleteRecursively()
        if (!temporary.mkdirs()) throw ModelInstallException(WRITE_FAILED)
        try {
            val expected = files.associateBy { it.name }
            val extracted = mutableSetOf<String>()
            val total = archive.length().coerceAtLeast(1)
            val counter = CountingInputStream(archive.inputStream().buffered(BUFFER))
            val buffer = ByteArray(BUFFER)
            val tar = try {
                // `true`: o .bz2 do sherpa-onnx pode ser vários fluxos seguidos (pbzip2); sem isso a
                // leitura pararia no fim do primeiro.
                TarArchiveInputStream(BZip2CompressorInputStream(counter, true))
            } catch (_: IOException) {
                counter.close()
                throw ModelInstallException(INVALID_ARCHIVE)
            }
            tar.use {
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = invalidOnIo { tar.nextEntry } ?: break
                    val parts = safeEntryPath(entry.name)
                    // Os arquivos vêm na raiz ou numa pasta com o nome do pacote; o resto (README,
                    // test_wavs/) fica de fora.
                    if (parts.size > 2) continue
                    val file = expected[parts.last()] ?: continue
                    if (!entry.isFile || entry.isLink || entry.isSymbolicLink ||
                        !extracted.add(file.name) || entry.size != file.bytes
                    ) {
                        throw ModelInstallException(INVALID_ARCHIVE)
                    }
                    val output = try {
                        File(temporary, file.name).outputStream().buffered(BUFFER)
                    } catch (_: IOException) {
                        throw ModelInstallException(WRITE_FAILED)
                    }
                    var copied = 0L
                    output.use {
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = invalidOnIo { tar.read(buffer) }
                            if (read < 0) break
                            copied += read
                            if (copied > file.bytes) throw ModelInstallException(INVALID_ARCHIVE)
                            try {
                                output.write(buffer, 0, read)
                            } catch (_: IOException) {
                                throw ModelInstallException(WRITE_FAILED)
                            }
                            onProgress((counter.count.toFloat() / total).coerceIn(0f, 0.999f))
                        }
                    }
                    if (copied != file.bytes) throw ModelInstallException(INVALID_ARCHIVE)
                }
            }
            if (extracted != expected.keys) throw ModelInstallException(INVALID_ARCHIVE)
            coroutineContext.ensureActive()

            // rename de pasta no mesmo volume é atômico: quem procura o modelo vê o antigo inteiro
            // ou o novo inteiro, nunca meio.
            val replaced = File(parent, destination.name + REPLACED_SUFFIX)
            replaced.deleteRecursively()
            if (destination.exists() && !destination.renameTo(replaced)) throw ModelInstallException(WRITE_FAILED)
            if (!temporary.renameTo(destination)) {
                replaced.renameTo(destination)
                throw ModelInstallException(WRITE_FAILED)
            }
            replaced.deleteRecursively()
            onProgress(1f)
        } catch (error: Throwable) {
            temporary.deleteRecursively()
            throw error
        }
    }

    // O commons-compress acusa cabeçalho corrompido com IllegalArgumentException, não só IOException.
    private inline fun <T> invalidOnIo(block: () -> T): T = try {
        block()
    } catch (_: IOException) {
        throw ModelInstallException(INVALID_ARCHIVE)
    } catch (_: IllegalArgumentException) {
        throw ModelInstallException(INVALID_ARCHIVE)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

object ModelDiskSpace {
    // O pacote e os arquivos extraídos convivem no disco durante a instalação; um download anterior
    // interrompido é apagado antes de começar, por isso conta como livre.
    fun enough(usableBytes: Long, reclaimableBytes: Long, requiredBytes: Long): Boolean =
        usableBytes + reclaimableBytes >= requiredBytes

    // Unidades decimais, como o gerenciador de arquivos do Android mostra.
    fun megabytes(bytes: Long): String = "${bytes / 1_000_000} MB"

    fun gigabytes(bytes: Long): String =
        String.format(Locale.forLanguageTag("pt-BR"), "%.1f GB", bytes / 1_000_000_000.0)
}

private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    @Volatile
    var count = 0L
        private set

    override fun read(): Int = super.read().also { if (it >= 0) count++ }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        super.read(b, off, len).also { if (it > 0) count += it }

    override fun skip(n: Long): Long = super.skip(n).also { count += it }
}
