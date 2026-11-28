/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.apk

/**
 * The package and version an APK declares, see [app.morphe.manager.util.PM.getApkIdentity].
 *
 * @param label The app name from the manifest, read only for an APK the platform refused and only
 *   when asked for. The platform reads the name of any other APK off its own package info.
 */
data class ApkIdentity(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val label: String? = null
)
