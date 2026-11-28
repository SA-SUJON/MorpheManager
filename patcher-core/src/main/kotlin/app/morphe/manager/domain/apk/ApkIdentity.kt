/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.apk

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.File
import java.util.zip.ZipFile

/** The package and version an APK declares in its manifest. */
data class ApkIdentity(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long
)

/**
 * The identity of this APK when it is a base module that requires an Android newer than [sdkInt],
 * which the platform refuses to parse. Null for any other file.
 */
fun File.newerSdkApkIdentityOrNull(sdkInt: Int): ApkIdentity? = runCatching {
    ZipFile(this).use { zip ->
        val entry = zip.getEntry("AndroidManifest.xml") ?: return null
        val manifest = zip.getInputStream(entry).use(AndroidManifestBlock::load)
        val minSdk = manifest.minSdkVersion ?: return null
        if (manifest.isSplit || minSdk <= sdkInt) return null
        ApkIdentity(
            packageName = manifest.packageName ?: return null,
            versionName = manifest.versionName,
            versionCode = manifest.versionCode?.toLong() ?: return null
        )
    }
}.getOrNull()
