/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/patcher/split/SplitApkInspector.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.patcher.split

import android.util.Log
import app.morphe.manager.util.LOG_TAG
import app.morphe.patcher.apk.SplitArchive
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SplitApkInspector {
    /**
     * Runs [block] on the APK that describes [source]: the file itself, or the base module of a
     * split archive, extracted for the call and deleted after it.
     */
    suspend fun <T> withRepresentativeApk(
        source: File,
        workspace: File,
        block: suspend (File) -> T
    ): T {
        if (!SplitArchive.isSplitArchive(source)) return block(source)

        val temp = File(
            workspace,
            "inspect-${UUID.randomUUID()}.apk"
        )

        try {
            withContext(Dispatchers.IO) {
                ZipFile(source).use { zip ->
                    val entry = SplitArchive.baseModule(zip)
                        ?: throw IOException("Split archive does not contain any APK entries.")
                    Log.d(LOG_TAG, "Reading ${source.name} through its module ${entry.name}")
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(temp.toPath()).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            return block(temp)
        } finally {
            temp.delete()
        }
    }
}
