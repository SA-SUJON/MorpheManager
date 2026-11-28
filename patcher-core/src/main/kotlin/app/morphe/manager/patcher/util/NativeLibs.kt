/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.util

import app.morphe.manager.domain.apk.ApkFileStamp
import app.morphe.manager.domain.apk.apkFileStampOrNull
import app.morphe.patcher.apk.Abi
import app.morphe.patcher.resource.CpuArchitecture
import com.reandroid.archive.ArchiveFile
import com.reandroid.archive.writer.ApkFileWriter
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Which ABIs an APK ships native libraries for, and which of them a run keeps when unused
 * libraries are stripped. The patcher drops the rest while it writes the output, and
 * [writeStripped] does the same for an original kept for later runs.
 */
object NativeLibs {
    // Screens listing saved APKs ask for the same files every time they open, and walking the
    // central directory of an APK hundreds of megabytes large is what makes them slow to fill.
    // Keyed by path, so a file rewritten in place replaces its entry rather than adding one
    private val abiCache = ConcurrentHashMap<String, Pair<ApkFileStamp, List<String>>>()

    /** [Abi.of] for [apkFile], an APK or split archive, read once until the file changes. */
    fun abisOf(apkFile: File): List<String> {
        val stamp = apkFile.apkFileStampOrNull() ?: return emptyList()
        abiCache[stamp.path]?.takeIf { it.first == stamp }?.let { return it.second }

        return Abi.of(apkFile).also { abis ->
            // Only the revision that was read, so a file rewritten meanwhile is read again
            if (apkFile.apkFileStampOrNull() == stamp) abiCache[stamp.path] = stamp to abis
        }
    }

    /**
     * The one ABI a run keeps out of [abisInApk], which is the first of [supportedAbis] the APK
     * has anything for. Null when it carries nothing the device runs.
     *
     * Public because it is also the answer to which architecture the output is built for, the
     * one ApkArchitectureResolver reports to patches that declare availability against it.
     */
    fun preferredAbi(abisInApk: Set<String>, supportedAbis: List<String>): String? =
        supportedAbis.firstOrNull { it in abisInApk }

    /**
     * The architectures the patcher keeps in [apkFile] when unused native libraries are stripped.
     * Empty, which keeps every library, when the APK carries nothing the device runs or an ABI
     * the patcher has no name for, rather than leaving the output without native code.
     */
    fun keptArchitectures(apkFile: File, supportedAbis: List<String>): Set<CpuArchitecture> =
        setOfNotNull(
            preferredAbi(abisOf(apkFile).toSet(), supportedAbis)
                ?.let(CpuArchitecture::valueOfOrNull)
        )

    /**
     * Whether [apkFile] carries an APK signing block. A copy without one gives patches no
     * certificates to read, so a signed copy of the same build is worth keeping in its place.
     */
    fun hasSigningBlock(apkFile: File): Boolean =
        runCatching { ArchiveFile(apkFile).use { it.apkSignatureBlock != null } }.getOrDefault(false)

    /**
     * Writes [apkFile] to [output] without the native libraries the patcher strips from it, see
     * [keptArchitectures], copying every other entry as it is stored. False, writing nothing, when
     * there is nothing to strip. The signing block is carried over for the certificates patches
     * read from it, but no longer matches the contents, so the result is an input to patch rather
     * than an APK to install.
     */
    fun writeStripped(apkFile: File, output: File, supportedAbis: List<String>): Boolean {
        val kept = keptArchitectures(apkFile, supportedAbis).singleOrNull()?.arch ?: return false
        if (abisOf(apkFile).all { it == kept }) return false

        ArchiveFile(apkFile).use { archive ->
            val sources = archive.getInputSources { entry ->
                entry.isFile && Abi.ofLibraryPath(entry.name).let { it == null || it == kept }
            }
            // Closes the file itself once written
            ApkFileWriter(output, sources).apply {
                apkSignatureBlock = archive.apkSignatureBlock
            }.write()
        }
        return true
    }
}
