/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.apk

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The platform refuses to parse an APK that requires a newer Android than the device runs, so its
 * identity is read from the manifest, while any other file is left to the platform.
 */
class NewerSdkApkIdentityTest {
    private val workspace = createTempDirectory("apk-identity").toFile()

    @AfterTest
    fun cleanup() {
        workspace.deleteRecursively()
    }

    private fun apk(minSdk: Int?, split: String? = null): File {
        val manifest = AndroidManifestBlock().apply {
            packageName = "com.example.app"
            versionName = "5.1.4"
            setVersionCode(260903140)
            if (minSdk != null) setMinSdkVersion(minSdk)
            if (split != null) setSplit(split, true)
            refresh()
        }.bytes
        val file = File(workspace, "base.apk")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(manifest)
            zip.closeEntry()
        }
        return file
    }

    @Test
    fun `a base that requires a newer Android is read from its manifest`() {
        assertEquals(
            ApkIdentity("com.example.app", "5.1.4", 260903140),
            apk(minSdk = 33).newerSdkApkIdentityOrNull(sdkInt = 31)
        )
    }

    @Test
    fun `an APK the device can run is left to the platform`() {
        assertNull(apk(minSdk = 31).newerSdkApkIdentityOrNull(sdkInt = 31))
        assertNull(apk(minSdk = null).newerSdkApkIdentityOrNull(sdkInt = 31))
    }

    @Test
    fun `a split module is not taken for an app`() {
        assertNull(apk(minSdk = 33, split = "config.arm64_v8a").newerSdkApkIdentityOrNull(sdkInt = 31))
    }

    @Test
    fun `a file without a readable manifest has no identity`() {
        val garbage = File(workspace, "garbage.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertNull(garbage.newerSdkApkIdentityOrNull(sdkInt = 31))
    }
}
